package com.visualnotes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.flywaydb.core.Flyway;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsConfigureUnicodeAndAreNotReapplied() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE()", String.class))
                .isEqualTo("utf8mb4");
        assertThat(jdbc.queryForObject("SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE()", String.class))
                .isEqualTo("utf8mb4_0900_ai_ci");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void validationRejectsChangesToAnAppliedMigration(@TempDir Path directory) throws Exception {
        try (var original = getClass().getResourceAsStream("/db/migration/V1__configure_database_unicode.sql")) {
            assertThat(original).isNotNull();
            String changed = new String(original.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("utf8mb4_0900_ai_ci", "utf8mb4_bin");
            Files.writeString(directory.resolve("V1__configure_database_unicode.sql"), changed);
        }
        var modified = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + directory)
                .load();
        var result = modified.validateWithResult();
        assertThat(result.validationSuccessful).isFalse();
        assertThat(result.invalidMigrations).hasSize(1);
        assertThat(result.invalidMigrations.getFirst().errorDetails.errorMessage).containsIgnoringCase("checksum");
    }

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
