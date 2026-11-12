package io.clueterra.questions.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.controller.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Token";

    private final byte[] secret;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InternalTokenFilter(
            @Value("${clueterra.internal.secret:}") String secret,
            ObjectMapper objectMapper,
            Clock clock) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        if (secret.length == 0) {
            reject(request, response, HttpStatus.SERVICE_UNAVAILABLE, "Internal token is not configured");
            return;
        }

        String token = request.getHeader(HEADER);
        if (token != null && MessageDigest.isEqual(secret, token.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }

        reject(request, response, HttpStatus.UNAUTHORIZED, "Invalid or missing internal token");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(
                Instant.now(clock), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
