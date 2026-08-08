package com.suaposta.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
final class SafeUpstreamErrorFilter implements GlobalFilter, Ordered {

    private static final String SAFE_ERROR_MESSAGE = "The request could not be completed.";

    private final ObjectMapper objectMapper;

    SafeUpstreamErrorFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var response = exchange.getResponse();
        response.beforeCommit(() -> {
            retainSafeHeaders(response.getHeaders());
            return Mono.empty();
        });

        var decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                var status = getStatusCode();
                if (status == null || !status.is5xxServerError()) {
                    retainSafeHeaders(getHeaders());
                    return super.writeWith(body);
                }

                return DataBufferUtils.join(body)
                        .switchIfEmpty(Mono.fromSupplier(
                                () -> exchange.getResponse().bufferFactory().wrap(new byte[0])))
                        .flatMap(dataBuffer -> writeSafeError(exchange, status, dataBuffer));
            }

            @Override
            public Mono<Void> writeAndFlushWith(
                    Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(publisher -> publisher));
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build())
                .onErrorResume(throwable -> handleUpstreamFailure(exchange));
    }

    private Mono<Void> handleUpstreamFailure(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(new IllegalStateException("Gateway response was already committed"));
        }

        var status = HttpStatus.BAD_GATEWAY;
        response.setStatusCode(status);
        return writeSafeError(exchange, status, null);
    }

    private Mono<Void> writeSafeError(
            ServerWebExchange exchange, HttpStatusCode status, DataBuffer upstreamBody) {
        try {
            var safeBody = objectMapper.writeValueAsBytes(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", status.value(),
                    "error", errorName(status),
                    "message", SAFE_ERROR_MESSAGE,
                    "path", exchange.getRequest().getPath().value()));

            release(upstreamBody);
            var response = exchange.getResponse();
            retainSafeHeaders(response.getHeaders());
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().setContentLength(safeBody.length);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(safeBody)));
        } catch (JsonProcessingException exception) {
            release(upstreamBody);
            var fallbackBody = ("{\"timestamp\":" + jsonString(Instant.now().toString())
                    + ",\"status\":" + status.value()
                    + ",\"error\":" + jsonString(errorName(status))
                    + ",\"message\":" + jsonString(SAFE_ERROR_MESSAGE)
                    + ",\"path\":" + jsonString(exchange.getRequest().getPath().value()) + "}")
                    .getBytes(StandardCharsets.UTF_8);
            var response = exchange.getResponse();
            retainSafeHeaders(response.getHeaders());
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().setContentLength(fallbackBody.length);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallbackBody)));
        }
    }

    private static String jsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                + "\"";
    }

    private static void release(DataBuffer upstreamBody) {
        if (upstreamBody != null) {
            DataBufferUtils.release(upstreamBody);
        }
    }

    private static void retainSafeHeaders(HttpHeaders headers) {
        var safeHeaders = new HttpHeaders();
        headers.forEach((name, values) -> {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)
                    || HttpHeaders.VARY.equalsIgnoreCase(name)
                    || name.regionMatches(true, 0, "Access-Control-", 0, "Access-Control-".length())) {
                safeHeaders.put(name, List.copyOf(values));
            }
        });
        headers.clear();
        headers.putAll(safeHeaders);
    }

    private static String errorName(HttpStatusCode status) {
        var resolvedStatus = HttpStatus.resolve(status.value());
        return resolvedStatus == null ? "Gateway Error" : resolvedStatus.getReasonPhrase();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
