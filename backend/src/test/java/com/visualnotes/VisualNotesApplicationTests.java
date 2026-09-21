package com.visualnotes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import com.visualnotes.model.Account;
import com.visualnotes.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.hibernate.ddl-auto=update")
class VisualNotesApplicationTests {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private AccountRepository accounts;

    @LocalServerPort
    private int port;

    @Test
    void hibernateCreatesSchemaAndJpaPersistsAndReloadsAccounts() {
        var saved = accounts.saveAndFlush(new Account("unicode-例@example.com", "test-hash"));
        assertThat(UUID.fromString(saved.getId())).isNotNull();
        var reloaded = accounts.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("unicode-例@example.com");
        assertThat(reloaded.getPasswordHash()).isEqualTo("test-hash");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

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
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
