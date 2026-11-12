package io.clueterra.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import io.clueterra.api.admin.console.controller.ConsoleLogController;
import io.clueterra.api.admin.console.dto.ConsoleStateDto;
import io.clueterra.api.admin.console.service.ConsoleLogService;
import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.auth.controller.AuthController;
import io.clueterra.api.auth.service.AuthService;
import io.clueterra.api.auth.service.JwtService;
import io.clueterra.api.auth.service.OAuth2Service;
import io.clueterra.api.filter.RateLimitingFilter;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.security.ClientIpResolver;
import io.clueterra.api.security.GuestTokenFilter;
import io.clueterra.api.security.JwtAuthenticationFilter;

@WebMvcTest(controllers = {
        AuthController.class,
        ConsoleLogController.class
})
@ActiveProfiles("prod")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GuestTokenFilter.class})
@TestPropertySource(properties = {
        "clueterra.cors.allowed-origins=http://localhost:3000"
})
class ProdProfileSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Environment environment;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private SettingsService settingsService;
    @MockitoBean
    private OAuth2Service oauth2Service;
    @MockitoBean
    private JwtProperties jwtProperties;
    @MockitoBean
    private ConsoleLogService consoleLogService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;
    @MockitoBean
    private LegalAcceptanceService legalAcceptanceService;
    @MockitoBean
    private ClientIpResolver clientIpResolver;
    @MockitoBean
    private AuthJdbcRepository authJdbcRepository;

    @BeforeEach
    void setUp() throws Exception {
        when(settingsService.isRegistrationEnabled()).thenReturn(true);
        when(consoleLogService.getState(any())).thenReturn(new ConsoleStateDto(null, List.of()));
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");
        doAnswer(invocation -> {
            invocation.<jakarta.servlet.FilterChain>getArgument(2)
                    .doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("prod profile disables springdoc endpoints")
    void prodProfile_disablesSpringdoc() throws Exception {
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();

        mockMvc.perform(get("/api-docs")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("prod profile keeps auth public and admin routes protected")
    void prodProfile_keepsAuthPublicAndAdminProtected() throws Exception {
        mockMvc.perform(get("/api/auth/registration-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get("/api/admin/console/state"))
                .andExpect(status().isUnauthorized());
    }
}
