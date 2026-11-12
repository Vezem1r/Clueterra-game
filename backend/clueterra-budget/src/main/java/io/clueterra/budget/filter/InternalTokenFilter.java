package io.clueterra.budget.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.config.BudgetInternalProperties;
import io.clueterra.budget.exception.ApiErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "X-Internal-Token";

    private final BudgetInternalProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String configuredSecret = properties.secret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE, "Internal token is not configured");
            return;
        }

        String suppliedToken = request.getHeader(TOKEN_HEADER);
        if (suppliedToken != null
                && MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            filterChain.doFilter(request, response);
            return;
        }

        writeError(response, HttpStatus.UNAUTHORIZED, "Invalid or missing internal token");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(status, message));
    }
}
