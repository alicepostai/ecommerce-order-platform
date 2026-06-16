package com.ecommerce.orders.infrastructure.web;

import com.ecommerce.orders.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(OpenApiConfig.class)
@DisplayName("OpenApiConfig — configuracao do bean OpenAPI")
class OpenApiDocTest {

    @Autowired
    OpenAPI openAPI;

    @Test
    @DisplayName("Versao OpenAPI e 3.1.0")
    void openapi_version_is_3_1_0() {
        assertThat(openAPI.getOpenapi()).isEqualTo("3.1.0");
    }

    @Test
    @DisplayName("Titulo da API esta correto")
    void info_title_is_correct() {
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Order Service API");
    }

    @Test
    @DisplayName("Esquema de segurança bearerAuth esta configurado como JWT")
    void bearerAuth_securityScheme_isConfigured() {
        var schemes = openAPI.getComponents().getSecuritySchemes();
        assertThat(schemes).containsKey("bearerAuth");

        SecurityScheme scheme = schemes.get("bearerAuth");
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }

    @Test
    @DisplayName("Security requirement global referencia bearerAuth")
    void security_requirement_references_bearerAuth() {
        assertThat(openAPI.getSecurity())
                .isNotEmpty()
                .anySatisfy(req -> assertThat(req).containsKey("bearerAuth"));
    }

    @Test
    @DisplayName("Informacoes de contato estao presentes")
    void info_contact_is_present() {
        assertThat(openAPI.getInfo().getContact()).isNotNull();
        assertThat(openAPI.getInfo().getContact().getEmail()).isEqualTo("dev@ecommerce.dev");
    }
}
