package com.visualnotes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VisualNotesApplicationTests {
    @LocalServerPort
    private int port;

    @Test
    void healthIsAvailableWithoutExposingDetails() throws Exception {
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        var health = new JsonMapper().readTree(response.body());
        assertThat(health.get("status").asString()).isEqualTo("UP");
        assertThat(health.has("details")).isFalse();
        assertThat(health.has("components")).isFalse();
    }

    @Test
    void otherManagementEndpointsAreNotExposed() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
