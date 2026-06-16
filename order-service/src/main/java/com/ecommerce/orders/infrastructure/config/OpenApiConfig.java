package com.ecommerce.orders.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
                .openapi("3.1.0")
                .info(new Info()
                        .title("Order Service API")
                        .version("1.0.0")
                        .description("Backend de pedidos de e-commerce — ciclo de vida completo: " +
                                "criação → itens → confirmação → pagamento → pago/cancelado.")
                        .contact(new Contact().name("Equipe E-commerce").email("dev@ecommerce.dev")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT RS256 emitido pelo identity provider. " +
                                        "Escopos: orders:read, orders:write, payments:read, payments:write")));
    }
}
