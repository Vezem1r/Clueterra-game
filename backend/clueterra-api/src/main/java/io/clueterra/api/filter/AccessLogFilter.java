package io.clueterra.api.filter;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.clueterra.api.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long ms = System.currentTimeMillis() - start;
            ACCESS_LOG.info("{} {} {} {}ms user={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    ms,
                    resolveUserId());
        }
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal p) {
            return String.valueOf(p.getId());
        }
        return "-";
    }
}
