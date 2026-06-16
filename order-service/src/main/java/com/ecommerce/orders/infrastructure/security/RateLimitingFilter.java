package com.ecommerce.orders.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnWebApplication
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final String PROBLEM_BASE = "https://api.ecommerce.dev/problems/";

    private final int maxRequests;
    private final long windowSeconds;
    private final ObjectMapper objectMapper;
    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${security.rate-limit.max-requests:50}") int maxRequests,
            @Value("${security.rate-limit.window-seconds:10}") long windowSeconds,
            ObjectMapper objectMapper) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);
        Instant now = Instant.now();

        var bucket = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (bucket) {
            Instant windowStart = now.minusSeconds(windowSeconds);
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(windowStart)) {
                bucket.pollFirst();
            }
            if (bucket.size() >= maxRequests) {
                log.warn("Rate limit exceeded for key: {}", key);
                writeTooManyRequests(response);
                return;
            }
            bucket.addLast(now);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {

        var auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ") && auth.length() > 20) {

            return "token:" + auth.substring(7, Math.min(auth.length(), 120));
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        var pd = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        pd.setType(URI.create(PROBLEM_BASE + "rate-limit-exceeded"));
        pd.setTitle("Too Many Requests");
        pd.setDetail("Rate limit exceeded — please slow down");
        pd.setProperty("correlationId", UUID.randomUUID().toString());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
