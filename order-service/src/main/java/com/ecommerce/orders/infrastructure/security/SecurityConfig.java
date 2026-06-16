package com.ecommerce.orders.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URI;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String PROBLEM_BASE = "https://api.ecommerce.dev/problems/";

    @Bean
    @ConditionalOnWebApplication
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                        .authenticationEntryPoint((req, res, e) ->
                                writeProblem(res, objectMapper, HttpStatus.UNAUTHORIZED, "unauthorized",
                                        "Unauthorized", "Authentication is required"))
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeProblem(res, objectMapper, HttpStatus.UNAUTHORIZED, "unauthorized",
                                        "Unauthorized", "Authentication is required"))
                        .accessDeniedHandler((req, res, e) -> {
                            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                            if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
                                writeProblem(res, objectMapper, HttpStatus.UNAUTHORIZED, "unauthorized",
                                        "Unauthorized", "Authentication is required");
                            } else {
                                writeProblem(res, objectMapper, HttpStatus.FORBIDDEN, "forbidden",
                                        "Forbidden", "Insufficient scope or permissions");
                            }
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    private void writeProblem(jakarta.servlet.http.HttpServletResponse response,
                              ObjectMapper objectMapper,
                              HttpStatus status,
                              String type,
                              String title,
                              String detail) throws java.io.IOException {
        var pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create(PROBLEM_BASE + type));
        pd.setTitle(title);
        pd.setDetail(detail);
        pd.setProperty("correlationId", UUID.randomUUID().toString());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
