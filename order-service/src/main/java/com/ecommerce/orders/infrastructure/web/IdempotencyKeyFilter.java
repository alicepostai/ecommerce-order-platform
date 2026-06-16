package com.ecommerce.orders.infrastructure.web;

import com.ecommerce.orders.application.port.out.IdempotencyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Component
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public IdempotencyKeyFilter(IdempotencyStore idempotencyStore, ObjectMapper objectMapper) {
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (idempotencyKey == null || !isMutation(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        byte[] bodyBytes = StreamUtils.copyToByteArray(request.getInputStream());
        String requestHash = sha256Hex(bodyBytes);
        String endpointScope = request.getMethod() + ":" + request.getRequestURI();

        var existing = idempotencyStore.find(idempotencyKey, endpointScope);
        if (existing.isPresent()) {
            var stored = existing.get();
            if (!stored.requestHash().equals(requestHash)) {
                writeMismatch(response);
                return;
            }
            replay(response, stored);
            return;
        }

        var requestWithBody = new CachedBodyRequest(request, bodyBytes);
        var responseWrapper = new ContentCachingResponseWrapper(response);

        chain.doFilter(requestWithBody, responseWrapper);

        if (responseWrapper.getStatus() < 500) {
            String body = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            idempotencyStore.store(idempotencyKey, endpointScope, requestHash, responseWrapper.getStatus(), body);
        }

        responseWrapper.copyBodyToResponse();
    }

    private boolean isMutation(String method) {
        return "POST".equals(method) || "DELETE".equals(method);
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            var sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void replay(HttpServletResponse response, IdempotencyStore.StoredResponse stored) throws IOException {
        response.setStatus(stored.status());
        String contentType = stored.status() >= 400
                ? MediaType.APPLICATION_PROBLEM_JSON_VALUE
                : MediaType.APPLICATION_JSON_VALUE;
        response.setContentType(contentType);
        if (stored.body() != null && !stored.body().isBlank()) {
            response.getWriter().write(stored.body());
        }
    }

    private void writeMismatch(HttpServletResponse response) throws IOException {
        var pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create("https://api.ecommerce.dev/problems/idempotency-key-mismatch"));
        pd.setTitle("Idempotency Key Mismatch");
        pd.setDetail("The Idempotency-Key was used with a different request payload");
        pd.setProperty("correlationId", UUID.randomUUID().toString());

        response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), pd);
    }

    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return bais.read(); }
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {}
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
        }
    }
}
