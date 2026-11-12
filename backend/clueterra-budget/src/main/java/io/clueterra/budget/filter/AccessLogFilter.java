package io.clueterra.budget.filter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            ACCESS_LOG.info(
                    "{} {} {} {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMillis);
        }
    }
}
