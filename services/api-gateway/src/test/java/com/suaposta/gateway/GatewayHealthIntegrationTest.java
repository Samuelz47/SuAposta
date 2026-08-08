package com.suaposta.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.context.WebServerApplicationContext;

class GatewayHealthIntegrationTest {

    private static final int DOCUMENTED_GATEWAY_PORT = 8080;

    @Test
    void should_expose_healthy_actuator_endpoint_on_documented_port() throws Exception {
        try (var context = ApplicationTestSupport.startApplication()) {
            assertThat(context).isInstanceOf(WebServerApplicationContext.class);
            var port = ((WebServerApplicationContext) context).getWebServer().getPort();
            assertThat(port).isEqualTo(DOCUMENTED_GATEWAY_PORT);

            var response = get(port, "/actuator/health");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
        }
    }

    private static HttpResponse<String> get(int port, String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

}
