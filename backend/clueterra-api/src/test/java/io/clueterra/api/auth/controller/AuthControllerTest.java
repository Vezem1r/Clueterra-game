package io.clueterra.api.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.auth.dto.AuthResponse;
import io.clueterra.api.auth.service.AuthService;
import io.clueterra.api.auth.service.OAuth2Service;
import io.clueterra.api.config.JwtProperties;
import io.clueterra.api.exception.GlobalExceptionHandler;
import io.clueterra.api.exception.TooManyLoginAttemptsException;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private OAuth2Service oauthService;
    @Mock
    private JwtProperties jwtProperties;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, settingsService, oauthService, jwtProperties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("login without TOTP requirement sets both auth cookies")
    void loginSetsCookies() throws Exception {
        AuthResponse response = authResponse("access-token", "refresh-token", false);
        ResponseCookie accessCookie = ResponseCookie.from("clueterra_access", "access-token")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").build();
        ResponseCookie refreshCookie = ResponseCookie.from("clueterra_refresh", "refresh-token")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth/refresh").build();
        ResponseCookie clearedGuest = clearedGuestCookie();

        when(authService.login(any(), isNull())).thenReturn(response);
        when(jwtProperties.getExpiration()).thenReturn(60_000L);
        when(jwtProperties.getRefreshExpiration()).thenReturn(120_000L);
        when(authService.buildAccessCookie("access-token", 60_000L)).thenReturn(accessCookie);
        when(authService.buildRefreshCookie("refresh-token", 120_000L)).thenReturn(refreshCookie);
        when(authService.clearGuestCookie()).thenReturn(clearedGuest);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpRequired").value(false))
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(3);
        assertThat(cookies.get(0)).isEqualTo(accessCookie.toString());
        assertThat(cookies.get(1)).isEqualTo(refreshCookie.toString());
        assertGuestClearCookie(cookies.get(2));
    }

    @Test
    @DisplayName("login on a locked account returns 429 with Retry-After")
    void loginOnLockedAccountReturnsTooManyRequests() throws Exception {
        when(authService.login(any(), isNull())).thenThrow(new TooManyLoginAttemptsException(120));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "120"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(120));
    }

    @Test
    @DisplayName("register rejects a password longer than 72 bytes as a field error")
    void registerRejectsPasswordOverBcryptLimit() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","username":"user","password":"%s",
                                 "termsVersion":"2026-04-21","privacyVersion":"2026-04-21"}
                                """.formatted("a".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password must not exceed 72 bytes"));
    }

    @Test
    @DisplayName("login with TOTP requirement does not set cookies before second factor")
    void loginWithTotpRequiredSkipsCookies() throws Exception {
        when(authService.login(any(), isNull())).thenReturn(authResponse(null, null, true));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpRequired").value(true))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    @DisplayName("login with guest cookie passes token to service")
    void loginWithGuestCookiePassesTokenToService() throws Exception {
        AuthResponse response = authResponse("access-token", "refresh-token", false);
        ResponseCookie accessCookie = ResponseCookie.from("clueterra_access", "access-token")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").build();
        ResponseCookie refreshCookie = ResponseCookie.from("clueterra_refresh", "refresh-token")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth/refresh").build();
        ResponseCookie clearedGuest = clearedGuestCookie();

        when(authService.login(any(), eq("guest-token"))).thenReturn(response);
        when(jwtProperties.getExpiration()).thenReturn(60_000L);
        when(jwtProperties.getRefreshExpiration()).thenReturn(120_000L);
        when(authService.buildAccessCookie("access-token", 60_000L)).thenReturn(accessCookie);
        when(authService.buildRefreshCookie("refresh-token", 120_000L)).thenReturn(refreshCookie);
        when(authService.clearGuestCookie()).thenReturn(clearedGuest);

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("ct_guest", "guest-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk());

        verify(authService).login(any(), eq("guest-token"));
    }

    @Test
    @DisplayName("register with guest cookie passes token to service and clears guest cookie")
    void registerWithGuestCookieClearsGuestCookie() throws Exception {
        AuthResponse response = authResponse("access-token", "refresh-token", false);
        ResponseCookie accessCookie = ResponseCookie.from("clueterra_access", "access-token")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").build();
        ResponseCookie refreshCookie = ResponseCookie.from("clueterra_refresh", "refresh-token")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth/refresh").build();
        ResponseCookie clearedGuest = clearedGuestCookie();

        when(authService.register(any(), eq("guest-token"))).thenReturn(response);
        when(jwtProperties.getExpiration()).thenReturn(60_000L);
        when(jwtProperties.getRefreshExpiration()).thenReturn(120_000L);
        when(authService.buildAccessCookie("access-token", 60_000L)).thenReturn(accessCookie);
        when(authService.buildRefreshCookie("refresh-token", 120_000L)).thenReturn(refreshCookie);
        when(authService.clearGuestCookie()).thenReturn(clearedGuest);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(new Cookie("ct_guest", "guest-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"new@example.com",
                                  "username":"newplayer",
                                  "password":"StrongPass123!",
                                  "termsVersion":"2026-04-21",
                                  "privacyVersion":"2026-04-21"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        verify(authService).register(any(), eq("guest-token"));
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(3);
        assertThat(cookies.get(0)).isEqualTo(accessCookie.toString());
        assertThat(cookies.get(1)).isEqualTo(refreshCookie.toString());
        assertThat(cookies.get(2))
                .contains("ct_guest=")
                .contains("Path=/api")
                .contains("Max-Age=0")
                .contains("SameSite=Lax");
    }

    @Test
    @DisplayName("refresh falls back to refresh cookie when request body is absent")
    void refreshUsesCookieFallback() throws Exception {
        AuthResponse response = authResponse("new-access", "new-refresh", false);
        ResponseCookie accessCookie = ResponseCookie.from("clueterra_access", "new-access")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").build();
        ResponseCookie refreshCookie = ResponseCookie.from("clueterra_refresh", "new-refresh")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth/refresh").build();
        ResponseCookie clearedGuest = clearedGuestCookie();

        when(authService.refresh("cookie-refresh")).thenReturn(response);
        when(jwtProperties.getExpiration()).thenReturn(60_000L);
        when(jwtProperties.getRefreshExpiration()).thenReturn(120_000L);
        when(authService.buildAccessCookie("new-access", 60_000L)).thenReturn(accessCookie);
        when(authService.buildRefreshCookie("new-refresh", 120_000L)).thenReturn(refreshCookie);
        when(authService.clearGuestCookie()).thenReturn(clearedGuest);

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("clueterra_refresh", "cookie-refresh")))
                .andExpect(status().isOk())
                .andReturn();

        verify(authService).refresh("cookie-refresh");
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(3);
        assertThat(cookies.get(0)).isEqualTo(accessCookie.toString());
        assertThat(cookies.get(1)).isEqualTo(refreshCookie.toString());
        assertGuestClearCookie(cookies.get(2));
    }

    @Test
    @DisplayName("verify-totp sets cookies once second factor succeeds")
    void verifyTotpSetsCookies() throws Exception {
        AuthResponse response = authResponse("access-after-totp", "refresh-after-totp", false);
        ResponseCookie accessCookie = ResponseCookie.from("clueterra_access", "access-after-totp")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").build();
        ResponseCookie refreshCookie = ResponseCookie.from("clueterra_refresh", "refresh-after-totp")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth/refresh").build();
        ResponseCookie clearedGuest = clearedGuestCookie();

        when(authService.verifyTotp(any(), isNull())).thenReturn(response);
        when(jwtProperties.getExpiration()).thenReturn(60_000L);
        when(jwtProperties.getRefreshExpiration()).thenReturn(120_000L);
        when(authService.buildAccessCookie("access-after-totp", 60_000L)).thenReturn(accessCookie);
        when(authService.buildRefreshCookie("refresh-after-totp", 120_000L)).thenReturn(refreshCookie);
        when(authService.clearGuestCookie()).thenReturn(clearedGuest);

        MvcResult result = mockMvc.perform(post("/api/auth/verify-totp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123","totpCode":"123456"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(3);
        assertThat(cookies.get(0)).isEqualTo(accessCookie.toString());
        assertThat(cookies.get(1)).isEqualTo(refreshCookie.toString());
        assertGuestClearCookie(cookies.get(2));
    }

    @Test
    @DisplayName("verify-totp with guest cookie passes token to service")
    void verifyTotpWithGuestCookiePassesTokenToService() throws Exception {
        AuthResponse response = authResponse("access-after-totp", "refresh-after-totp", false);
        ResponseCookie accessCookie = ResponseCookie.from("clueterra_access", "access-after-totp")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").build();
        ResponseCookie refreshCookie = ResponseCookie.from("clueterra_refresh", "refresh-after-totp")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth/refresh").build();
        ResponseCookie clearedGuest = clearedGuestCookie();

        when(authService.verifyTotp(any(), eq("guest-token"))).thenReturn(response);
        when(jwtProperties.getExpiration()).thenReturn(60_000L);
        when(jwtProperties.getRefreshExpiration()).thenReturn(120_000L);
        when(authService.buildAccessCookie("access-after-totp", 60_000L)).thenReturn(accessCookie);
        when(authService.buildRefreshCookie("refresh-after-totp", 120_000L)).thenReturn(refreshCookie);
        when(authService.clearGuestCookie()).thenReturn(clearedGuest);

        mockMvc.perform(post("/api/auth/verify-totp")
                        .cookie(new Cookie("ct_guest", "guest-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123","totpCode":"123456"}
                                """))
                .andExpect(status().isOk());

        verify(authService).verifyTotp(any(), eq("guest-token"));
    }

    @Test
    @DisplayName("logout revokes the refresh session and clears both cookies")
    void logoutClearsCookies() throws Exception {
        ResponseCookie clearedAccess = ResponseCookie.from("clueterra_access", "")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").maxAge(0).build();
        ResponseCookie clearedRefresh = ResponseCookie.from("clueterra_refresh", "")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth/refresh").maxAge(0).build();
        ResponseCookie clearedGuest = clearedGuestCookie();
        when(authService.clearAccessCookie()).thenReturn(clearedAccess);
        when(authService.clearRefreshCookie()).thenReturn(clearedRefresh);
        when(authService.clearGuestCookie()).thenReturn(clearedGuest);

        MvcResult result = mockMvc.perform(delete("/api/auth/refresh")
                        .cookie(new Cookie("clueterra_refresh", "cookie-refresh")))
                .andExpect(status().isOk())
                .andReturn();

        verify(authService).logout("cookie-refresh");
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(3);
        assertThat(cookies.get(0))
                .contains("clueterra_access=")
                .contains("Path=/api")
                .contains("Max-Age=0")
                .contains("SameSite=Lax");
        assertThat(cookies.get(1))
                .contains("clueterra_refresh=")
                .contains("Path=/api/auth/refresh")
                .contains("Max-Age=0")
                .contains("SameSite=Lax");
        assertThat(cookies.get(2))
                .contains("ct_guest=")
                .contains("Path=/api")
                .contains("Max-Age=0")
                .contains("SameSite=Lax");
    }

    private static AuthResponse authResponse(String accessToken, String refreshToken, boolean totpRequired) {
        return new AuthResponse(
                accessToken, refreshToken, "user@example.com", "user", "USER", 60_000L, totpRequired, false, 1L);
    }

    private static ResponseCookie clearedGuestCookie() {
        return ResponseCookie.from("ct_guest", "")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").maxAge(0).build();
    }

    private static void assertGuestClearCookie(String cookie) {
        assertThat(cookie)
                .contains("ct_guest=")
                .contains("Path=/api")
                .contains("Max-Age=0")
                .contains("SameSite=Lax");
    }
}
