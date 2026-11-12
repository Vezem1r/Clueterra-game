package io.clueterra.api.security;

import java.net.InetAddress;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.clueterra.api.config.RateLimitingProperties;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(RateLimitingProperties properties) {
        this.trustedProxies = properties.getTrustedProxies().stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = normalize(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String cfConnectingIp = normalize(request.getHeader("CF-Connecting-IP"));
        if (cfConnectingIp != null) {
            return cfConnectingIp;
        }

        String forwardedFor = normalize(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor.split(",")[0].trim();
        }

        return remoteAddr;
    }

    public boolean hasForwardedHeaders(HttpServletRequest request) {
        return normalize(request.getHeader("CF-Connecting-IP")) != null
                || normalize(request.getHeader("X-Forwarded-For")) != null;
    }

    public boolean isDirectLoopback(HttpServletRequest request) {
        try {
            String remoteAddr = normalize(request.getRemoteAddr());
            if (remoteAddr == null || hasForwardedHeaders(request)) {
                return false;
            }
            return InetAddress.getByName(remoteAddr).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTrustedProxy(String remoteAddr) {
        return remoteAddr != null && trustedProxies.contains(remoteAddr);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
