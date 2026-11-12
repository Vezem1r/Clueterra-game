package io.clueterra.api.auth.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.auth.dto.AuthResponse;
import io.clueterra.api.auth.dto.CompleteOAuthRequest;
import io.clueterra.api.auth.dto.LoginRequest;
import io.clueterra.api.auth.dto.OAuthCodeExchangeRequest;
import io.clueterra.api.auth.dto.PendingOAuthUser;
import io.clueterra.api.auth.dto.RegisterRequest;
import io.clueterra.api.auth.dto.TotpLoginRequest;
import io.clueterra.api.auth.service.AuthService;
import io.clueterra.api.auth.service.OAuth2Service;
import io.clueterra.api.config.JwtProperties;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication and registration")
public class AuthController {

    private final AuthService authService;
    private final SettingsService settingsService;
    private final OAuth2Service oauthService;
    private final JwtProperties jwtProperties;

    @GetMapping("/registration-status")
    public ResponseEntity<Map<String, Boolean>> getRegistrationStatus() {
        return ResponseEntity.ok(Map.of("enabled", settingsService.isRegistrationEnabled()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            @CookieValue(name = "ct_guest", required = false) String guestToken,
            HttpServletResponse res) {
        AuthResponse auth = authService.register(request, guestToken);
        addAuthCookies(res, auth);
        clearGuestCookie(res);
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            @CookieValue(name = "ct_guest", required = false) String guestToken,
            HttpServletResponse res) {
        AuthResponse auth = authService.login(request, guestToken);
        if (!auth.totpRequired()) {
            addAuthCookies(res, auth);
            clearGuestCookie(res);
        }
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/verify-totp")
    public ResponseEntity<AuthResponse> verifyTotp(
            @Valid @RequestBody TotpLoginRequest request,
            @CookieValue(name = "ct_guest", required = false) String guestToken,
            HttpServletResponse res) {
        AuthResponse auth = authService.verifyTotp(request, guestToken);
        addAuthCookies(res, auth);
        clearGuestCookie(res);
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/oauth2/complete-registration")
    public ResponseEntity<AuthResponse> completeOAuthRegistration(
            @Valid @RequestBody CompleteOAuthRequest request,
            @CookieValue(name = "ct_guest", required = false) String guestToken,
            HttpServletResponse res) {
        PendingOAuthUser pending = oauthService.resolvePendingUser(request.pendingToken());
        AuthResponse auth = authService.completeOAuthRegistration(
                pending.email(), request.username(), pending.provider(), pending.oauthId(),
                pending.avatarUrl(), request.termsVersion(), request.privacyVersion(), guestToken);
        addAuthCookies(res, auth);
        clearGuestCookie(res);
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/oauth2/exchange")
    public ResponseEntity<AuthResponse> exchangeOAuthCode(@Valid @RequestBody OAuthCodeExchangeRequest request, HttpServletResponse res) {
        AuthResponse auth = oauthService.exchangeAuthCode(request.code());
        addAuthCookies(res, auth);
        clearGuestCookie(res);
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "clueterra_refresh", required = false) String cookieRefresh,
                                                HttpServletResponse res) {
        if (cookieRefresh == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        AuthResponse auth = authService.refresh(cookieRefresh);
        addAuthCookies(res, auth);
        clearGuestCookie(res);
        return ResponseEntity.ok(auth);
    }

    @DeleteMapping("/refresh")
    public ResponseEntity<Void> logout(@CookieValue(name = "clueterra_refresh", required = false) String cookieRefresh,
                                       HttpServletResponse res) {
        authService.logout(cookieRefresh);
        res.addHeader(HttpHeaders.SET_COOKIE, authService.clearAccessCookie().toString());
        res.addHeader(HttpHeaders.SET_COOKIE, authService.clearRefreshCookie().toString());
        clearGuestCookie(res);
        return ResponseEntity.ok().build();
    }

    private void addAuthCookies(HttpServletResponse res, AuthResponse auth) {
        if (auth.accessToken() != null) {
            res.addHeader(HttpHeaders.SET_COOKIE, authService.buildAccessCookie(auth.accessToken(), jwtProperties.getExpiration()).toString());
        }
        if (auth.refreshToken() != null) {
            res.addHeader(HttpHeaders.SET_COOKIE, authService.buildRefreshCookie(auth.refreshToken(), jwtProperties.getRefreshExpiration()).toString());
        }
    }

    private void clearGuestCookie(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, authService.clearGuestCookie().toString());
    }
}
