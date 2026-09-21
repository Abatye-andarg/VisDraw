package com.visualnotes;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.visualnotes.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.session.cookie.secure=false", "spring.jpa.hibernate.ddl-auto=update"})
class AccountApiTests {
    private static final String PASSWORD = "A long test passphrase!";
    private static final JsonMapper JSON = new JsonMapper();

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withInitScript("database/existing-accounts.sql");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PasswordEncoder passwords;

    @Test
    void existingAccountsSurviveHibernateSchemaUpdate() {
        var account = accounts.findById("00000000-0000-0000-0000-000000000001").orElseThrow();
        assertThat(account.getEmail()).isEqualTo("existing@example.com");
        assertThat(account.getPasswordHash()).isEqualTo("{pbkdf2-sha256-v1}" + "0".repeat(96));
        assertThat(account.getCreatedAt()).isNotNull();
    }

    @Test
    void registrationHashesPasswordsAndDoesNotSignInAutomatically() throws Exception {
        String email = email();
        try (var client = new Client()) {
            var response = client.register("  " + email.toUpperCase() + "  ", PASSWORD);
            assertThat(response.statusCode()).isEqualTo(201);
            JsonNode account = body(response);
            assertThat(account.size()).isEqualTo(2);
            assertThat(account.get("email").asString()).isEqualTo(email);
            assertThat(UUID.fromString(account.get("id").asString())).isNotNull();
            String hash = hash(email);
            assertThat(hash).isNotEqualTo(PASSWORD).startsWith("{pbkdf2-sha256-v1}");
            assertThat(passwords.matches(PASSWORD, hash)).isTrue();
            assertProblem(client.get("/api/auth/me"), 401);
            String second = email();
            assertThat(client.register(second, PASSWORD).statusCode()).isEqualTo(201);
            assertThat(hash(second)).isNotEqualTo(hash);
        }
    }

    @Test
    void loginRotatesSessionAndCsrfAndLogoutInvalidatesSession() throws Exception {
        String email = email();
        try (var client = new Client(); var replay = new Client()) {
            assertThat(client.register(email, PASSWORD).statusCode()).isEqualTo(201);
            String beforeLoginToken = client.csrf();
            String beforeLoginSession = client.sessionId();
            var login = client.post("/api/auth/login", "application/x-www-form-urlencoded",
                    form("  " + email.toUpperCase() + "  ", PASSWORD), beforeLoginToken);
            assertThat(login.statusCode()).isEqualTo(204);
            assertThat(client.sessionId()).isNotEqualTo(beforeLoginSession);
            assertThat(login.headers().allValues("set-cookie").toString())
                    .containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Lax");
            assertThat(body(client.get("/api/auth/me")).get("email").asString()).isEqualTo(email);
            assertProblem(replay.getWithSession("/api/auth/me", beforeLoginSession), 401);
            assertProblem(client.post("/api/auth/logout", "application/x-www-form-urlencoded", "", beforeLoginToken), 403);
            assertThat(client.get("/api/auth/me").statusCode()).isEqualTo(200);
            String signedInSession = client.sessionId();
            assertThat(client.post("/api/auth/logout", "application/x-www-form-urlencoded", "", client.csrf()).statusCode())
                    .isEqualTo(204);
            assertProblem(client.get("/api/auth/me"), 401);
            assertProblem(replay.getWithSession("/api/auth/me", signedInSession), 401);
            assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(204);
        }
    }

    @Test
    void incorrectPasswordAndUnknownEmailHaveTheSameFailure() throws Exception {
        try (var client = new Client()) {
            String email = email();
            assertThat(client.register(email, PASSWORD).statusCode()).isEqualTo(201);
            var incorrect = client.login(email, "An incorrect passphrase");
            var unknown = client.login(email(), "An incorrect passphrase");
            assertProblem(incorrect, 401);
            assertProblem(unknown, 401);
            assertThat(body(incorrect)).isEqualTo(body(unknown));
            assertProblem(client.get("/api/auth/me"), 401);
        }
    }

    @Test
    void stateChangingRequestsRequireCsrf() throws Exception {
        try (var client = new Client()) {
            var register = client.post("/api/auth/register", "application/json",
                    JSON.writeValueAsString(Map.of("email", email(), "password", PASSWORD)), null);
            assertProblem(register, 403);
            assertProblem(client.post("/api/auth/login", "application/x-www-form-urlencoded", form(email(), PASSWORD), null), 403);
            String email = email();
            assertThat(client.register(email, PASSWORD).statusCode()).isEqualTo(201);
            assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(204);
            assertProblem(client.post("/api/auth/logout", "application/x-www-form-urlencoded", "", null), 403);
            assertThat(client.get("/api/auth/me").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void registrationValidatesInputAndDoesNotEchoPasswords() throws Exception {
        try (var client = new Client()) {
            for (String password : new String[] {"short-secret", "x".repeat(129), " ".repeat(20)}) {
                var response = client.register(email(), password);
                assertProblem(response, 400);
                assertThat(response.body()).doesNotContain(password);
                assertThat(body(response).get("errors").has("password")).isTrue();
            }
            assertProblem(client.register("not-an-email", PASSWORD), 400);
            assertProblem(client.post("/api/auth/register", "application/json", "{}", client.csrf()), 400);
            assertProblem(client.post("/api/auth/register", "application/json", "{", client.csrf()), 400);
            assertThat(accounts.findByEmail("not-an-email")).isEmpty();
        }
    }

    @Test
    void duplicateEmailIsCaseInsensitiveAndSafeUnderConcurrentRegistration() throws Exception {
        String email = email();
        try (var first = new Client(); var second = new Client()) {
            var one = CompletableFuture.supplyAsync(() -> registerUnchecked(first, email));
            var two = CompletableFuture.supplyAsync(() -> registerUnchecked(second, email.toUpperCase()));
            assertThat(new int[] {one.join().statusCode(), two.join().statusCode()}).containsExactlyInAnyOrder(201, 409);
            var duplicate = first.register(email.toUpperCase(), PASSWORD);
            assertProblem(duplicate, 409);
            assertThat(accounts.findByEmail(email)).isPresent();
        }
    }

    @Test
    void currentAccountComesFromTheSessionAndManagementEndpointsRemainHidden() throws Exception {
        try (var first = new Client(); var second = new Client()) {
            String firstEmail = email();
            String secondEmail = email();
            var firstAccount = body(first.register(firstEmail, PASSWORD));
            var secondAccount = body(second.register(secondEmail, PASSWORD));
            assertThat(first.login(firstEmail, PASSWORD).statusCode()).isEqualTo(204);
            assertThat(second.login(secondEmail, PASSWORD).statusCode()).isEqualTo(204);
            assertThat(body(first.get("/api/auth/me?id=" + secondAccount.get("id").asString())))
                    .isEqualTo(firstAccount);
            assertThat(body(second.get("/api/auth/me"))).isEqualTo(secondAccount);
            assertThat(first.get("/actuator/env").statusCode()).isEqualTo(404);
        }
    }

    private HttpResponse<String> registerUnchecked(Client client, String email) {
        try {
            return client.register(email, PASSWORD);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private String hash(String email) {
        return accounts.findByEmail(email).orElseThrow().getPasswordHash();
    }

    private static String email() {
        return UUID.randomUUID() + "@example.com";
    }

    private static JsonNode body(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    private static void assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("application/problem+json");
        assertThat(body(response).get("status").asInt()).isEqualTo(status);
        assertThat(response.headers().firstValue("location")).isEmpty();
    }

    private static String form(String email, String password) {
        return "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
    }

    private class Client implements AutoCloseable {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build();

        HttpResponse<String> get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET());
        }

        HttpResponse<String> getWithSession(String path, String session) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).header("Cookie", "JSESSIONID=" + session).GET());
        }

        String csrf() throws Exception {
            var response = get("/api/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(body(response).get("headerName").asString()).isEqualTo("X-CSRF-TOKEN");
            return body(response).get("token").asString();
        }

        String sessionId() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
        }

        HttpResponse<String> register(String email, String password) throws Exception {
            return post("/api/auth/register", "application/json",
                    JSON.writeValueAsString(Map.of("email", email, "password", password)), csrf());
        }

        HttpResponse<String> login(String email, String password) throws Exception {
            return post("/api/auth/login", "application/x-www-form-urlencoded", form(email, password), csrf());
        }

        HttpResponse<String> post(String path, String contentType, String content, String csrf) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofString(content));
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            return send(request);
        }

        private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
            return http.send(request.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
        }

        private URI uri(String path) {
            return URI.create("http://127.0.0.1:" + port + path);
        }

        @Override
        public void close() {
            http.close();
        }
    }
}
