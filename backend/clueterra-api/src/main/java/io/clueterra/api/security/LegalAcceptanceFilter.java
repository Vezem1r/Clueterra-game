package io.clueterra.api.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.exception.ApiErrorResponse;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LegalAcceptanceFilter extends OncePerRequestFilter {

    private final LegalAcceptanceService legalAcceptanceService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;

        if (!(principal instanceof UserPrincipal userPrincipal)
                || !legalAcceptanceService.requiresAcceptance(userPrincipal)
                || isAllowedWithoutCurrentAcceptance(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.ofCode(
                        HttpStatus.FORBIDDEN,
                        "You must accept the current Terms of Service and acknowledge the current Privacy Policy before continuing.",
                        "LEGAL_ACCEPTANCE_REQUIRED"));
    }

    private boolean isAllowedWithoutCurrentAcceptance(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/api/auth/") || path.startsWith("/api/legal/")) {
            return true;
        }
        if (path.equals("/api/health") || path.startsWith("/actuator/")) {
            return true;
        }
        if (path.startsWith("/api/game/today")) {
            return true;
        }
        switch (path) {
            case "/api/budget/challenges/today", "/api/budget/sessions/today", "/api/budget/sessions/guest", "/api/game/sessions/guest",
                 "/api/rivers/session", "/api/rivers/session/guest", "/api/rivers/names",
                 "/api/game/guest/progress", "/api/profile/guest/badges", "/api/game/guest/state", "/api/game/countries/search",
                 "/api/leaderboard" -> {
                return true;
            }
        }
        if (path.startsWith("/api/assets/")
                || path.startsWith("/api/s/")
                || path.startsWith("/api/project-pages")) {
            return true;
        }
        return "GET".equalsIgnoreCase(method)
                && path.matches("^/api/profile/[^/]+(?:/badges)?$");
    }
}
