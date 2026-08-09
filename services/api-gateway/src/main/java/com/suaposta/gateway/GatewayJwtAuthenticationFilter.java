package com.suaposta.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
final class GatewayJwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SAFE_ERROR_MESSAGE = "The request could not be completed.";
    private static final Pattern BASE64_URL = Pattern.compile("[A-Za-z0-9_-]+");

    private final ObjectMapper objectMapper;
    private final byte[] signingSecret;

    GatewayJwtAuthenticationFilter(
            ObjectMapper objectMapper,
            @Value("${gateway.jwt.secret}") String signingSecret) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalArgumentException("The JWT signing secret must be configured");
        }
        this.objectMapper = objectMapper;
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        if (isPublicAuthenticationEndpoint(exchange.getRequest())) {
            var publicRequest = exchange.getRequest().mutate()
                    .headers(headers -> headers.remove(USER_ID_HEADER))
                    .build();
            return chain.filter(exchange.mutate().request(publicRequest).build());
        }

        if (!requiresAuthentication(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        var userId = authenticatedUserId(exchange.getRequest());
        if (userId == null) {
            return writeUnauthorized(exchange);
        }

        var authenticatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.remove(USER_ID_HEADER);
                    headers.set(USER_ID_HEADER, userId);
                })
                .build();

        return chain.filter(exchange.mutate().request(authenticatedRequest).build());
    }

    private static boolean requiresAuthentication(ServerHttpRequest request) {
        var path = request.getPath().value();
        if (isPublicAuthenticationEndpoint(request, path)) {
            return false;
        }
        return path.equals("/bets")
                || path.startsWith("/bets/")
                || path.equals("/analytics")
                || path.startsWith("/analytics/")
                || path.startsWith("/auth/");
    }

    private static boolean isPublicAuthenticationEndpoint(ServerHttpRequest request, String path) {
        return HttpMethod.POST.equals(request.getMethod())
                && (path.equals("/auth/register") || path.equals("/auth/login"));
    }

    private static boolean isPublicAuthenticationEndpoint(ServerHttpRequest request) {
        return isPublicAuthenticationEndpoint(request, request.getPath().value());
    }

    private String authenticatedUserId(ServerHttpRequest request) {
        var authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(
                true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        var token = authorization.substring(BEARER_PREFIX.length());
        if (token.isBlank()) {
            return null;
        }

        try {
            var parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return null;
            }

            var header = readJsonObject(decodeBase64Url(parts[0]));
            if (!"HS256".equals(textClaim(header, "alg"))) {
                return null;
            }

            var expectedSignature = hmacSha256(parts[0] + "." + parts[1]);
            var receivedSignature = decodeBase64Url(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, receivedSignature)) {
                return null;
            }

            var claims = readJsonObject(decodeBase64Url(parts[1]));
            var subject = textClaim(claims, "sub");
            var issuedAt = numericClaim(claims, "iat");
            var expiration = numericClaim(claims, "exp");
            if (subject == null || issuedAt == null || expiration == null
                    || expiration <= Instant.now().getEpochSecond()) {
                return null;
            }

            var userId = UUID.fromString(subject);
            if (!userId.toString().equalsIgnoreCase(subject)) {
                return null;
            }
            return userId.toString();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    private JsonNode readJsonObject(byte[] value) throws IOException {
        try (JsonParser parser = objectMapper.getFactory().createParser(value)) {
            JsonNode node = objectMapper.readTree(parser);
            if (node == null || !node.isObject() || parser.nextToken() != null) {
                throw new IllegalArgumentException("JWT JSON value must be one object");
            }
            return node;
        }
    }

    private static byte[] decodeBase64Url(String value) {
        if (!BASE64_URL.matcher(value).matches()) {
            throw new IllegalArgumentException("JWT segment is not valid Base64URL");
        }
        return Base64.getUrlDecoder().decode(value);
    }

    private static String textClaim(JsonNode claims, String name) {
        var claim = claims.get(name);
        return claim != null && claim.isTextual() && !claim.textValue().isBlank()
                ? claim.textValue()
                : null;
    }

    private static Long numericClaim(JsonNode claims, String name) {
        var claim = claims.get(name);
        return claim != null && claim.isIntegralNumber() && claim.canConvertToLong()
                ? claim.longValue()
                : null;
    }

    private byte[] hmacSha256(String signingInput) throws GeneralSecurityException {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(signingSecret, "HmacSHA256"));
        return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", HttpStatus.UNAUTHORIZED.value(),
                    "error", HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    "message", SAFE_ERROR_MESSAGE,
                    "path", exchange.getRequest().getPath().value()));
        } catch (JsonProcessingException exception) {
            body = ("{\"timestamp\":\"" + Instant.now()
                    + "\",\"status\":401,\"error\":\"Unauthorized\"")
                    .concat(",\"message\":\"The request could not be completed.\",\"path\":\"")
                    .concat(exchange.getRequest().getPath().value().replace("\\", "\\\\").replace("\"", "\\\""))
                    .concat("\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        response.getHeaders().setContentLength(body.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
