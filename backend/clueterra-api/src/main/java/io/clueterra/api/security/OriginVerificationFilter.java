package io.clueterra.api.security;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.exception.ApiErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OriginVerificationFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String ERROR_CODE = "ORIGIN_NOT_ALLOWED";

    private final Set<String> trustedOrigins;
    private final ObjectMapper objectMapper;

    public OriginVerificationFilter(List<String> allowedOrigins, ObjectMapper objectMapper) {
        this.trustedOrigins = allowedOrigins.stream()
                .map(OriginVerificationFilter::normalize)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        Optional<String> sourceOrigin = sourceOrigin(request);
        boolean trusted = sourceOrigin
                .map(origin -> origin.equals(requestOrigin(request)) || trustedOrigins.contains(origin))
                .orElseGet(() -> request.getHeader(HttpHeaders.COOKIE) == null);
        if (trusted) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rejected {} {} from untrusted origin {}",
                request.getMethod(), request.getRequestURI(), sourceOrigin.orElse("<none>"));
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.ofCode(HttpStatus.FORBIDDEN, "Cross-origin request rejected", ERROR_CODE));
    }

    private static Optional<String> sourceOrigin(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.ORIGIN))
                .or(() -> Optional.ofNullable(request.getHeader(HttpHeaders.REFERER))
                        .flatMap(OriginVerificationFilter::originOf))
                .map(OriginVerificationFilter::normalize);
    }

    private static Optional<String> originOf(String url) {
        try {
            URI uri = new URI(url);
            return Optional.ofNullable(uri.getHost())
                    .filter(host -> uri.getScheme() != null)
                    .map(host -> uri.getScheme() + "://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort()));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static String requestOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return normalize(scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port));
    }

    private static String normalize(String origin) {
        String trimmed = origin.trim().toLowerCase(Locale.ROOT);
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
