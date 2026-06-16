package com.ecommerce.orders.infrastructure.observability;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CorrelationId — propagacao do header X-Correlation-Id (T17)")
class CorrelationIdFilterTest {

    static final String MAPPINGS_PATH = Path.of("../wiremock/mappings").toAbsolutePath().toString();
    static final String FILES_PATH    = Path.of("../wiremock/__files").toAbsolutePath().toString();
    static final Path   PRIVATE_KEY   = Path.of("../rsa-private-key-pkcs8.pem").toAbsolutePath();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> wireMock = new GenericContainer<>("wiremock/wiremock:latest")
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
    }

    @BeforeAll
    static void initJwtHelper() {
        jwtHelper = new TestJwtHelper(PRIVATE_KEY, "test-issuer");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String baseUrl() { return "http://localhost:" + port; }

    @Test
    @DisplayName("Resposta devolve X-Correlation-Id quando enviado na requisição")
    void request_withCorrelationId_echoedInResponse() {
        var correlationId = UUID.randomUUID().toString();
        var token = jwtHelper.generate("user-1", "orders:read");

        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Correlation-Id", correlationId);

        var response = rest.exchange(
                baseUrl() + "/api/v1/orders?customerId=11111111-1111-1111-1111-111111111111",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Resposta gera X-Correlation-Id quando não enviado na requisição")
    void request_withoutCorrelationId_generatesNewOne() {
        var token = jwtHelper.generate("user-2", "orders:read");

        var headers = new HttpHeaders();
        headers.setBearerAuth(token);

        var response = rest.exchange(
                baseUrl() + "/api/v1/orders?customerId=11111111-1111-1111-1111-111111111111",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        var returned = response.getHeaders().getFirst("X-Correlation-Id");
        assertThat(returned).isNotNull().isNotBlank();
        // Should be a valid UUID
        assertThat(UUID.fromString(returned)).isNotNull();
    }
}
