package io.clueterra.api.security;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.clueterra.api.auth.repository.AuthJdbcRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuestTokenFilter extends OncePerRequestFilter {

    private static final String GUEST_COOKIE = "ct_guest";

    private final AuthJdbcRepository authRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String guestToken = resolveGuestToken(request);
        if (guestToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        authRepository.findByGuestToken(guestToken).ifPresent(user -> {
            UserPrincipal principal = new UserPrincipal(
                    user.id(), user.email(), user.passwordHash(), user.username(),
                    user.role(), user.enabled(),
                    user.termsVersionAccepted(), user.privacyVersionAccepted(), true);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        });

        filterChain.doFilter(request, response);
    }

    private String resolveGuestToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (GUEST_COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
