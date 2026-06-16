package com.ecommerce.orders.infrastructure.security;

import com.ecommerce.orders.testutil.TestJwtHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Segurança JWT — 401/403 RFC 7807, headers, rate limit (Gherkin S5)")
class SecurityIntegrationTest {

    static final String MAPPINGS_PATH = Path.of("../wiremock/mappings").toAbsolutePath().toString();
    static final String FILES_PATH    = Path.of("../wiremock/__files").toAbsolutePath().toString();
    static final Path   PRIVATE_KEY   = Path.of("../rsa-private-key-pkcs8.pem").toAbsolutePath();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> wireMock = new GenericContainer<>("wiremock/wiremock:3")
            .withCommand("--global-response-templating")
            .withFileSystemBind(MAPPINGS_PATH, "/home/wiremock/mappings", BindMode.READ_ONLY)
            .withFileSystemBind(FILES_PATH,    "/home/wiremock/__files",  BindMode.READ_ONLY)
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    static TestJwtHelper jwtHelper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        var wmUrl = "http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080);
        registry.add("external.customer.base-url",        () -> wmUrl);
        registry.add("external.catalog.base-url",         () -> wmUrl);
        registry.add("external.payment-gateway.base-url", () -> wmUrl);
        registry.add("external.notification.base-url",    () -> wmUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> wmUrl + "/auth/.well-known/jwks.json");
        // Low rate limit for testing
        registry.add("security.rate-limit.max-requests",  () -> "5");
        registry.add("security.rate-limit.window-seconds", () -> "10");
    }

    @BeforeAll
    static void initJwtHelper() {
        jwtHelper = new TestJwtHelper(PRIVATE_KEY, "test-issuer");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String baseUrl() { return "http://localhost:" + port; }

    private HttpHeaders bearerHeaders(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ── Gherkin: sem token → 401 ─────────────────────────────────────────────

    @Test
    @DisplayName("Gherkin: sem Authorization → 401 application/problem+json")
    void noToken_returns401() {
        var response = rest.postForEntity(
                baseUrl() + "/api/v1/orders",
                new HttpEntity<>("{\"customerId\":\"11111111-1111-1111-1111-111111111111\"}",
                        new HttpHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.toString()).contains("problem+json"));
        assertThat(response.getBody()).contains("unauthorized");
    }

    // ── Gherkin: escopo errado → 403 ─────────────────────────────────────────

    @Test
    @DisplayName("Gherkin: token com orders:read → POST /confirm retorna 403 problem+json")
    void wrongScope_returns403() {
        var token = jwtHelper.generate("user-1", "orders:read");
        var headers = bearerHeaders(token);

        var response = rest.exchange(
                baseUrl() + "/api/v1/orders/00000000-0000-0000-0000-000000000001/confirm",
                HttpMethod.POST,
                new HttpEntity<>("", headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.toString()).contains("problem+json"));
        assertThat(response.getBody()).contains("forbidden");
    }

    // ── Gherkin: token válido com escopo correto → 404 (não 401/403) ─────────

    @Test
    @DisplayName("Token válido com orders:write → chega ao handler (404 para pedido inexistente)")
    void validToken_correctScope_reachesHandler() {
        var token = jwtHelper.generate("user-1", "orders:write");
        var headers = bearerHeaders(token);

        var response = rest.exchange(
                baseUrl() + "/api/v1/orders/00000000-0000-0000-0000-000000000001/confirm",
                HttpMethod.POST,
                new HttpEntity<>("", headers),
                String.class);

        // Should reach the handler (returns 404 not-found, not 401/403)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Security headers ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Resposta inclui headers de segurança X-Content-Type-Options e Cache-Control")
    void response_includesSecurityHeaders() {
        var token = jwtHelper.generate("user-1", "orders:read");
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);

        var response = rest.exchange(
                baseUrl() + "/api/v1/orders?customerId=11111111-1111-1111-1111-111111111111",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("Cache-Control")).isNotNull();
    }

    // ── Rate limiting → 429 ───────────────────────────────────────────────────

    @Test
    @DisplayName("Rate limit excedido → 429 application/problem+json")
    void rateLimitExceeded_returns429() {
        var token = jwtHelper.generate("user-rate", "orders:read");
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var entity = new HttpEntity<>(headers);

        // max-requests = 5; make 6 calls
        ResponseEntity<String> last = null;
        for (int i = 0; i <= 5; i++) {
            last = rest.exchange(
                    baseUrl() + "/api/v1/orders?customerId=11111111-1111-1111-1111-111111111111",
                    HttpMethod.GET, entity, String.class);
        }

        assertThat(last).isNotNull();
        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(last.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.toString()).contains("problem+json"));
        assertThat(last.getBody()).contains("rate-limit-exceeded");
    }
}
