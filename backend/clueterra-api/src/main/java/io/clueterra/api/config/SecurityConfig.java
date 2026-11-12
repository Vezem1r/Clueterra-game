package io.clueterra.api.config;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import io.clueterra.api.exception.ApiErrorResponse;
import io.clueterra.api.security.GuestTokenFilter;
import io.clueterra.api.security.JwtAuthenticationFilter;
import io.clueterra.api.security.LegalAcceptanceFilter;
import io.clueterra.api.security.OriginVerificationFilter;
import io.clueterra.api.security.ClientIpResolver;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final long CORS_MAX_AGE_SECONDS = 3600L;
    private static final String ROLE_ADMIN = "ADMIN";
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization",
            "Content-Type",
            "Accept-Language",
            "Cookie",
            "X-Requested-With");
    private static final String[] AUTHENTICATED_ENDPOINTS = {
            "/api/profile",
            "/api/profile/avatar",
            "/api/profile/guest",
            "/api/profile/badges",
            "/api/profile/badges/**",
            "/api/profile/guest/badges",
            "/api/profile/streak-freeze-notice/acknowledge",
            "/api/hints/report",
    };
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/**",
            "/api/legal/**",
            "/api/health",
            "/api/game/today/**",
            "/api/game/results/today",
            "/api/game/guest/progress",
            "/api/game/guest/state",
            "/api/game/sessions/today/summary",
            "/api/game/sessions/guest",
            "/api/game/sessions/{sessionId}/**",
            "/api/game/countries/search",
            "/api/budget/**",
            "/api/rivers/**",
            "/api/silhouettes/**",
            "/api/flag-game/**",
            "/api/quiz-game/**",
            "/api/project-pages",
            "/api/project-pages/**",
            "/api/leaderboard",
            "/api/assets/**",
            "/api/s/**",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/prometheus",
    };
    private static final String[] ADMIN_ONLY_ENDPOINTS = {
            "/api/admin/**",
            "/actuator/**",
            "/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final GuestTokenFilter guestTokenFilter;
    private final LegalAcceptanceFilter legalAcceptanceFilter;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    @Value("#{'${clueterra.cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(ct -> {
                        })
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(AUTHENTICATED_ENDPOINTS)
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/api/profile/{username}", "/api/profile/{username}/badges", "/api/badges/samples")
                                        .permitAll()
                                        .requestMatchers(PUBLIC_ENDPOINTS)
                                        .permitAll()
                                        .requestMatchers(ADMIN_ONLY_ENDPOINTS)
                                        .hasRole(ROLE_ADMIN)
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new OriginVerificationFilter(allowedOrigins, objectMapper), CorsFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(guestTokenFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(legalAcceptanceFilter, GuestTokenFilter.class)
                .build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.requestRejectedHandler(this::writeRejectedRequestResponse);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(List.of("Set-Cookie"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(CORS_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType("application/json");
            objectMapper.writeValue(response.getOutputStream(),
                    ApiErrorResponse.of(HttpStatus.UNAUTHORIZED, "Authentication required"));
        };
    }

    private void writeRejectedRequestResponse(HttpServletRequest request, HttpServletResponse response, RequestRejectedException exception) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad request"));
    }
}
