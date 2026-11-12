package io.clueterra.api.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.clueterra.api.auth.dto.AuthResponse;
import io.clueterra.api.auth.dto.OAuthUserInfo;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.auth.service.AuthService;
import io.clueterra.api.auth.service.OAuth2Service;
import io.clueterra.api.config.JwtProperties;
import io.clueterra.api.config.OAuthProperties;
import io.clueterra.api.exception.GlobalExceptionHandler;

@ExtendWith(MockitoExtension.class)
class OAuth2ControllerTest {

    @Mock
    private OAuth2Service oauthService;
    @Mock
    private AuthService authService;
    @Mock
    private JwtProperties jwtProperties;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OAuthProperties properties = new OAuthProperties();
        properties.setBackendBaseUrl("https://api.example.com");
        properties.setFrontendRedirectUrl("https://frontend.example.com");

        mockMvc = MockMvcBuilders
                .standaloneSetup(new OAuth2Controller(oauthService, authService, properties, jwtProperties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("existing oauth user is redirected with cookies and clean frontend status")
    void callbackForExistingUserRedirectsWithCookies() throws Exception {
        OAuthUserInfo userInfo = new OAuthUserInfo(
                "user@example.com",
                "User",
                "https://avatars.githubusercontent.com/u/1",
                "oauth-1",
                true);
        AuthResponse tokens = new AuthResponse(
                "access", "refresh", "user@example.com", "user", "USER", 60_000L, false, false, 1L);
        ResponseCookie accessCookie = ResponseCookie.from("clueterra_access", "access")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").build();
        ResponseCookie refreshCookie = ResponseCookie.from("clueterra_refresh", "refresh")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth/refresh").build();

        when(oauthService.validateAndConsumeState("state-ok")).thenReturn(true);
        when(oauthService.exchangeCodeForUserInfo(
                "github",
                "provider-code",
                "https://api.example.com/api/auth/oauth2/callback/github"))
                .thenReturn(userInfo);
        when(oauthService.findExistingOAuthUser("github", "oauth-1"))
                .thenReturn(Optional.of(userRow()));
        when(authService.createTokensForPrincipal(any())).thenReturn(tokens);
        when(jwtProperties.getExpiration()).thenReturn(60_000L);
        when(jwtProperties.getRefreshExpiration()).thenReturn(120_000L);
        when(authService.buildAccessCookie("access", 60_000L)).thenReturn(accessCookie);
        when(authService.buildRefreshCookie("refresh", 120_000L)).thenReturn(refreshCookie);

        MvcResult result = mockMvc.perform(get("/api/auth/oauth2/callback/github")
                        .param("code", "provider-code")
                        .param("state", "state-ok")
                        .cookie(new Cookie("oauth_state", "state-ok")))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("https://frontend.example.com/oauth/callback?status=authenticated");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("oauth_state=")
                        .contains("Path=/api/auth/oauth2/callback/github")
                        .contains("Secure")
                        .contains("SameSite=Lax"))
                .contains(accessCookie.toString())
                .contains(refreshCookie.toString());
    }

    @Test
    @DisplayName("existing oauth user with guest cookie claims guest progress and clears guest cookie")
    void callbackForExistingUserWithGuestCookieClaimsProgress() throws Exception {
        OAuthUserInfo userInfo = new OAuthUserInfo(
                "user@example.com",
                "User",
                "https://avatars.githubusercontent.com/u/1",
                "oauth-1",
                true);
        AuthResponse tokens = new AuthResponse(
                "access", "refresh", "user@example.com", "user", "USER", 60_000L, false, false, 1L);
        ResponseCookie accessCookie = ResponseCookie.from("clueterra_access", "access")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").build();
        ResponseCookie refreshCookie = ResponseCookie.from("clueterra_refresh", "refresh")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth/refresh").build();
        ResponseCookie clearedGuest = ResponseCookie.from("ct_guest", "")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api").maxAge(0).build();

        when(oauthService.validateAndConsumeState("state-ok")).thenReturn(true);
        when(oauthService.exchangeCodeForUserInfo(
                "github",
                "provider-code",
                "https://api.example.com/api/auth/oauth2/callback/github"))
                .thenReturn(userInfo);
        when(oauthService.findExistingOAuthUser("github", "oauth-1"))
                .thenReturn(Optional.of(userRow()));
        when(authService.createTokensForPrincipal(any())).thenReturn(tokens);
        when(jwtProperties.getExpiration()).thenReturn(60_000L);
        when(jwtProperties.getRefreshExpiration()).thenReturn(120_000L);
        when(authService.buildAccessCookie("access", 60_000L)).thenReturn(accessCookie);
        when(authService.buildRefreshCookie("refresh", 120_000L)).thenReturn(refreshCookie);
        when(authService.clearGuestCookie()).thenReturn(clearedGuest);

        MvcResult result = mockMvc.perform(get("/api/auth/oauth2/callback/github")
                        .param("code", "provider-code")
                        .param("state", "state-ok")
                        .cookie(new Cookie("oauth_state", "state-ok"))
                        .cookie(new Cookie("ct_guest", "guest-token")))
                .andExpect(status().isFound())
                .andReturn();

        verify(authService).claimGuestProgress("guest-token", 1L);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .contains(accessCookie.toString())
                .contains(refreshCookie.toString())
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("ct_guest=")
                        .contains("Path=/api")
                        .contains("Max-Age=0")
                        .contains("SameSite=Lax"));
    }

    @Test
    @DisplayName("unknown oauth user is redirected with pending registration token")
    void callbackForNewUserRedirectsWithPendingToken() throws Exception {
        OAuthUserInfo userInfo = new OAuthUserInfo(
                "new@example.com",
                "New User",
                "https://lh3.googleusercontent.com/avatar",
                "oauth-2",
                true);

        when(oauthService.validateAndConsumeState("state-ok")).thenReturn(true);
        when(oauthService.exchangeCodeForUserInfo(
                "google",
                "provider-code",
                "https://api.example.com/api/auth/oauth2/callback/google"))
                .thenReturn(userInfo);
        when(oauthService.findExistingOAuthUser("google", "oauth-2"))
                .thenReturn(Optional.empty());
        when(oauthService.findExistingUserByEmail("new@example.com"))
                .thenReturn(Optional.empty());
        when(oauthService.storePendingUser(
                "new@example.com",
                "New User",
                "https://lh3.googleusercontent.com/avatar",
                "oauth-2",
                "google"))
                .thenReturn("pending-xyz");

        MvcResult result = mockMvc.perform(get("/api/auth/oauth2/callback/google")
                        .param("code", "provider-code")
                        .param("state", "state-ok")
                        .cookie(new Cookie("oauth_state", "state-ok")))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("https://frontend.example.com/oauth/callback?pending=pending-xyz");
    }

    @Test
    @DisplayName("invalid oauth state redirects with error and clears state cookie")
    void invalidStateRedirectsWithError() throws Exception {
        when(oauthService.validateAndConsumeState("bad-state")).thenReturn(false);

        MvcResult result = mockMvc.perform(get("/api/auth/oauth2/callback/github")
                        .param("code", "provider-code")
                        .param("state", "bad-state")
                        .cookie(new Cookie("oauth_state", "bad-state")))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("https://frontend.example.com/oauth/callback?error=invalid_state");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("oauth_state=")
                .contains("Path=/api/auth/oauth2/callback/github")
                .contains("Secure")
                .contains("SameSite=Lax");
    }

    @Test
    @DisplayName("email-matched local account is not auto-linked through OAuth")
    void callbackForExistingEmailRedirectsToLoginRequirement() throws Exception {
        OAuthUserInfo userInfo = new OAuthUserInfo(
                "user@example.com",
                "User",
                "https://avatars.githubusercontent.com/u/1",
                "oauth-1",
                true);

        when(oauthService.validateAndConsumeState("state-ok")).thenReturn(true);
        when(oauthService.exchangeCodeForUserInfo(
                "github",
                "provider-code",
                "https://api.example.com/api/auth/oauth2/callback/github"))
                .thenReturn(userInfo);
        when(oauthService.findExistingOAuthUser("github", "oauth-1"))
                .thenReturn(Optional.empty());
        when(oauthService.findExistingUserByEmail("user@example.com"))
                .thenReturn(Optional.of(userRow()));

        MvcResult result = mockMvc.perform(get("/api/auth/oauth2/callback/github")
                        .param("code", "provider-code")
                        .param("state", "state-ok")
                        .cookie(new Cookie("oauth_state", "state-ok")))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("https://frontend.example.com/oauth/callback?error=oauth_account_exists");
    }

    private static UserRow userRow() {
        return new UserRow(1L, "user@example.com", "hash", "user", "USER", true, null, false, null, null, null, null, false, null);
    }
}
