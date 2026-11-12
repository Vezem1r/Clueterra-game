package io.clueterra.api.auth.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.auth.dto.AuthResponse;
import io.clueterra.api.auth.dto.OAuthUserInfo;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.auth.service.AuthService;
import io.clueterra.api.auth.service.OAuth2Service;
import io.clueterra.api.config.JwtProperties;
import io.clueterra.api.config.OAuthProperties;
import io.clueterra.api.security.UserPrincipal;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/auth/oauth2")
@RequiredArgsConstructor
@Tag(name = "OAuth2", description = "OAuth2 social login")
@Slf4j
public class OAuth2Controller {

    private static final String STATE_COOKIE = "oauth_state";
    private static final String CALLBACK_PATH = "/api/auth/oauth2/callback/";

    private final OAuth2Service oauthService;
    private final AuthService authService;
    private final OAuthProperties oauthProperties;
    private final JwtProperties jwtProperties;

    @GetMapping("/{provider}")
    public ResponseEntity<Void> redirectToProvider(@PathVariable String provider) {
        String state = oauthService.generateAndStoreState();
        String callbackUrl = buildCallbackUrl(provider);
        String authUrl = oauthService.getAuthorizationUrl(provider, callbackUrl, state);

        ResponseCookie stateCookie = ResponseCookie.from(STATE_COOKIE, state)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(10))
                .path(CALLBACK_PATH + provider)
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authUrl)
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .build();
    }

    @GetMapping("/callback/{provider}")
    public ResponseEntity<Void> handleCallback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String state,
            @CookieValue(name = STATE_COOKIE, required = false) String cookieState,
            @CookieValue(name = "ct_guest", required = false) String guestToken) {

        ResponseCookie clearCookie = ResponseCookie.from(STATE_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .maxAge(0)
                .sameSite("Lax")
                .path(CALLBACK_PATH + provider)
                .build();

        if (error != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, oauthProperties.getFrontendRedirectUrl()
                            + "/oauth/callback?error=access_denied")
                    .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                    .build();
        }

        if (code == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, oauthProperties.getFrontendRedirectUrl()
                            + "/oauth/callback?error=missing_code")
                    .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                    .build();
        }

        if (!oauthService.validateAndConsumeState(state) || !state.equals(cookieState)) {
            log.warn("OAuth2 state validation failed for provider {}: requestStatePresent={}, cookieStatePresent={}",
                    provider, state != null, cookieState != null);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, oauthProperties.getFrontendRedirectUrl()
                            + "/oauth/callback?error=invalid_state")
                    .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                    .build();
        }

        String callbackUrl = buildCallbackUrl(provider);
        OAuthUserInfo userInfo = oauthService.exchangeCodeForUserInfo(provider, code, callbackUrl);

        var existingOAuth = oauthService.findExistingOAuthUser(provider, userInfo.oauthId());
        if (existingOAuth.isPresent()) {
            return handleExistingOAuthUser(existingOAuth.get(), clearCookie, guestToken);
        }

        if (userInfo.email() == null || !userInfo.emailVerified()) {
            return redirectToFrontendError("oauth_email_not_verified", clearCookie);
        }

        var existingEmail = oauthService.findExistingUserByEmail(userInfo.email());
        if (existingEmail.isPresent()) {
            return redirectToFrontendError(
                    existingEmail.get().enabled() ? "oauth_account_exists" : "oauth_account_disabled",
                    clearCookie);
        }

        String pendingToken = oauthService.storePendingUser(
                userInfo.email(), userInfo.name(), userInfo.avatarUrl(), userInfo.oauthId(), provider);
        return redirectToFrontendPending(pendingToken, clearCookie);
    }

    private ResponseEntity<Void> handleExistingOAuthUser(UserRow user, ResponseCookie clearCookie, String guestToken) {
        if (!user.enabled()) {
            return redirectToFrontendError("oauth_account_disabled", clearCookie);
        }
        if (requiresPrimaryLogin(user)) {
            return redirectToFrontendError("oauth_use_password_login", clearCookie);
        }
        return loginAndRedirect(user, clearCookie, guestToken);
    }

    private ResponseEntity<Void> loginAndRedirect(UserRow user, ResponseCookie clearCookie, String guestToken) {
        if (hasText(guestToken)) {
            authService.claimGuestProgress(guestToken, user.id());
        }

        UserPrincipal principal = new UserPrincipal(
                user.id(), user.email(), user.passwordHash(), user.username(), user.role(), user.enabled(),
                user.termsVersionAccepted(), user.privacyVersionAccepted());
        AuthResponse tokens = authService.createTokensForPrincipal(principal);
        var response = ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, oauthProperties.getFrontendRedirectUrl() + "/oauth/callback?status=authenticated")
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .header(HttpHeaders.SET_COOKIE,
                        authService.buildAccessCookie(tokens.accessToken(), jwtProperties.getExpiration()).toString())
                .header(HttpHeaders.SET_COOKIE,
                        authService.buildRefreshCookie(tokens.refreshToken(), jwtProperties.getRefreshExpiration()).toString());

        if (hasText(guestToken)) {
            response.header(HttpHeaders.SET_COOKIE, authService.clearGuestCookie().toString());
        }

        return response.build();
    }

    private ResponseEntity<Void> redirectToFrontendPending(String pendingToken, ResponseCookie clearCookie) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION,
                        oauthProperties.getFrontendRedirectUrl() + "/oauth/callback?pending=" + encode(pendingToken))
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    private ResponseEntity<Void> redirectToFrontendError(String errorCode, ResponseCookie clearCookie) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION,
                        oauthProperties.getFrontendRedirectUrl() + "/oauth/callback?error=" + encode(errorCode))
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    private boolean requiresPrimaryLogin(UserRow user) {
        return "ADMIN".equalsIgnoreCase(user.role()) || user.totpEnabled();
    }

    private String buildCallbackUrl(String provider) {
        return oauthProperties.getBackendBaseUrl() + "/api/auth/oauth2/callback/" + provider;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
