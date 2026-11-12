package io.clueterra.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.auth.dto.AuthResponse;
import io.clueterra.api.auth.dto.LoginRequest;
import io.clueterra.api.auth.dto.RegisterRequest;
import io.clueterra.api.auth.dto.TotpLoginRequest;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.config.JwtProperties;
import io.clueterra.api.config.TotpProperties;
import io.clueterra.api.exception.InvalidCredentialsException;
import io.clueterra.api.exception.ResourceConflictException;
import io.clueterra.api.exception.TooManyLoginAttemptsException;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.profile.service.AvatarImageService;
import io.clueterra.api.profile.service.ProcessedAvatar;
import io.clueterra.api.profile.repository.ProfileJdbcRepository;
import io.clueterra.api.security.UserPrincipal;
import io.clueterra.api.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthJdbcRepository authRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SettingsService settingsService;
    @Mock
    private TotpService totpService;
    @Mock
    private StorageService storageService;
    @Mock
    private ProfileJdbcRepository profileRepository;
    @Mock
    private LegalAcceptanceService legalAcceptanceService;
    @Mock
    private GuestProgressClaimService guestProgressClaimService;
    @Mock
    private AvatarImageService avatarImageService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginAttemptLimiter loginAttemptLimiter;

    private static final String ISSUED_REFRESH_TOKEN = "issued-refresh-token";

    private AuthService authService;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("01234567890123456789012345678901");
        jwtProperties.setExpiration(60_000L);
        jwtProperties.setRefreshExpiration(120_000L);

        TotpProperties totpProperties = new TotpProperties();

        jwtService = new JwtService(jwtProperties);
        lenient().when(refreshTokenService.issue(anyLong())).thenReturn(ISSUED_REFRESH_TOKEN);
        authService = new AuthService(
                authRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                jwtProperties,
                totpProperties,
                authenticationManager,
                settingsService,
                totpService,
                storageService,
                profileRepository,
                legalAcceptanceService,
                guestProgressClaimService,
                avatarImageService,
                loginAttemptLimiter);
    }

    @Test
    @DisplayName("login consumes an account attempt before checking the password and resets it on success")
    void loginConsumesAccountAttemptAndResetsOnSuccess() {
        UserPrincipal principal = principal(42L, "user@example.com", "USER");
        when(authenticationManager.authenticate(any())).thenReturn(authentication(principal));
        when(authRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(userRow(42L, "user@example.com", "user", false, null)));

        authService.login(new LoginRequest(" User@Example.com ", "password123"), null);

        InOrder order = inOrder(loginAttemptLimiter, authenticationManager);
        order.verify(loginAttemptLimiter).acquire("user@example.com");
        order.verify(authenticationManager).authenticate(any());
        order.verify(loginAttemptLimiter).reset("user@example.com");
    }

    @Test
    @DisplayName("login with a wrong password keeps the consumed account attempt")
    void loginWithWrongPasswordKeepsAttempt() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong-password"), null))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptLimiter).acquire("user@example.com");
        verify(loginAttemptLimiter, never()).reset(any());
    }

    @Test
    @DisplayName("login on a locked account is rejected without checking the password")
    void lockedAccountSkipsPasswordCheck() {
        doThrow(new TooManyLoginAttemptsException(60)).when(loginAttemptLimiter).acquire("user@example.com");

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "password123"), null))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("a correct password does not reset attempts while the second factor is still pending")
    void totpRequiredLoginKeepsAttempt() {
        UserPrincipal principal = principal(42L, "admin@example.com", "ADMIN");
        when(authenticationManager.authenticate(any())).thenReturn(authentication(principal));
        when(authRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(userRow(42L, "admin@example.com", "admin", true, "secret")));

        authService.login(new LoginRequest("admin@example.com", "password123"), null);

        verify(loginAttemptLimiter).acquire("admin@example.com");
        verify(loginAttemptLimiter, never()).reset(any());
    }

    @Test
    @DisplayName("verifyTotp on a locked account is rejected before checking the password or code")
    void lockedAccountSkipsTotpCheck() {
        doThrow(new TooManyLoginAttemptsException(60)).when(loginAttemptLimiter).acquire("user@example.com");

        assertThatThrownBy(() -> authService.verifyTotp(
                new TotpLoginRequest("user@example.com", "password123", "123456"), null))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        verify(authenticationManager, never()).authenticate(any());
        verify(totpService, never()).isCodeInvalid(anyLong(), any(), any());
    }

    @Test
    @DisplayName("login returns totpRequired without issuing tokens when user has TOTP enabled")
    void loginWithTotpEnabled_requiresSecondFactor() {
        UserPrincipal principal = principal(42L, "admin@example.com", "ADMIN");
        when(authenticationManager.authenticate(any())).thenReturn(authentication(principal));
        when(authRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(userRow(42L, "admin@example.com", "admin", true, "secret")));

        AuthResponse response = authService.login(new LoginRequest("admin@example.com", "password123"), null);

        assertThat(response.totpRequired()).isTrue();
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.userId()).isNull();
        verify(guestProgressClaimService, never()).claimTodayProgress(any(), anyLong());
    }

    @Test
    @DisplayName("login claims guest progress after password authentication succeeds")
    void loginWithGuestTokenClaimsProgress() {
        UserPrincipal principal = principal(42L, "user@example.com", "USER");
        when(authenticationManager.authenticate(any())).thenReturn(authentication(principal));
        when(authRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(userRow(42L, "user@example.com", "user", false, null)));

        AuthResponse response = authService.login(
                new LoginRequest("user@example.com", "password123"),
                "guest-token");

        assertThat(response.userId()).isEqualTo(42L);
        verify(guestProgressClaimService).claimTodayProgress("guest-token", 42L);
    }

    @Test
    @DisplayName("verifyTotp issues typed access and refresh tokens when code is valid")
    void verifyTotpReturnsTokens() {
        UserPrincipal principal = principal(7L, "user@example.com", "USER");
        when(authenticationManager.authenticate(any())).thenReturn(authentication(principal));
        when(authRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(userRow(7L, "user@example.com", "user", true, "otp-secret")));
        when(totpService.isCodeInvalid(7L, "otp-secret", "123456")).thenReturn(false);

        AuthResponse response = authService.verifyTotp(
                new TotpLoginRequest("user@example.com", "password123", "123456"),
                "guest-token");

        assertThat(response.totpRequired()).isFalse();
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(jwtService.isAccessToken(response.accessToken())).isTrue();
        assertThat(response.refreshToken()).isEqualTo(ISSUED_REFRESH_TOKEN);
        verify(guestProgressClaimService).claimTodayProgress("guest-token", 7L);
        verify(loginAttemptLimiter).acquire("user@example.com");
        verify(loginAttemptLimiter).reset("user@example.com");
    }

    @Test
    @DisplayName("verifyTotp rejects an invalid or already used code without issuing tokens")
    void verifyTotpRejectsInvalidOrReplayedCode() {
        UserPrincipal principal = principal(7L, "user@example.com", "USER");
        when(authenticationManager.authenticate(any())).thenReturn(authentication(principal));
        when(authRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(userRow(7L, "user@example.com", "user", true, "otp-secret")));
        when(totpService.isCodeInvalid(7L, "otp-secret", "123456")).thenReturn(true);

        assertThatThrownBy(() -> authService.verifyTotp(
                new TotpLoginRequest("user@example.com", "password123", "123456"),
                "guest-token"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(guestProgressClaimService, never()).claimTodayProgress(any(), any());
        verify(loginAttemptLimiter, never()).reset(any());
    }

    @Test
    @DisplayName("refresh rejects tokens that cannot be rotated without touching the user")
    void refreshRejectsTokensThatCannotBeRotated() {
        when(refreshTokenService.rotate("stale-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("stale-token"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(authRepository, never()).findById(any());
    }

    @Test
    @DisplayName("refresh returns a fresh access token with the rotated refresh token")
    void refreshReturnsRotatedToken() {
        when(refreshTokenService.rotate("current-token"))
                .thenReturn(Optional.of(new RotatedRefreshToken(9L, "rotated-token")));
        when(authRepository.findById(9L))
                .thenReturn(Optional.of(userRow(9L, "refresh@example.com", "player", false, null)));

        AuthResponse response = authService.refresh("current-token");

        assertThat(jwtService.isAccessToken(response.accessToken())).isTrue();
        assertThat(response.refreshToken()).isEqualTo("rotated-token");
        assertThat(response.userId()).isEqualTo(9L);
        verify(refreshTokenService, never()).issue(anyLong());
    }

    @Test
    @DisplayName("refresh rejects disabled users and revokes their sessions")
    void refreshRejectsDisabledUsers() {
        when(refreshTokenService.rotate("current-token"))
                .thenReturn(Optional.of(new RotatedRefreshToken(10L, "rotated-token")));
        when(authRepository.findById(10L))
                .thenReturn(Optional.of(new UserRow(
                        10L, "disabled@example.com", "hash", "disabled", "USER", false, null, false, null, null, null, null, false, null)));

        assertThatThrownBy(() -> authService.refresh("current-token"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService).revokeAll(10L);
    }

    @Test
    @DisplayName("logout revokes the presented refresh token session")
    void logoutRevokesRefreshToken() {
        authService.logout("current-token");

        verify(refreshTokenService).revoke("current-token");
    }

    @Test
    @DisplayName("register uses the username provided in the request")
    void registerUsesProvidedUsername() {
        when(settingsService.isRegistrationEnabled()).thenReturn(true);
        when(authRepository.existsByEmail("new.player@example.com")).thenReturn(false);
        when(authRepository.existsByUsername("coolplayer")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123!")).thenReturn("hashed-password");
        when(authRepository.insert(
                "new.player@example.com",
                "coolplayer",
                "hashed-password",
                "2026-04-21",
                "2026-04-21"))
                .thenReturn(44L);

        AuthResponse response = authService.register(new RegisterRequest(
                "new.player@example.com",
                "coolplayer",
                "StrongPass123!",
                "2026-04-21",
                "2026-04-21"), null);

        assertThat(response.username()).isEqualTo("coolplayer");
        assertThat(response.userId()).isEqualTo(44L);
    }

    @Test
    @DisplayName("register canonicalizes email and trims username before identity checks and insert")
    void registerNormalizesIdentity() {
        when(settingsService.isRegistrationEnabled()).thenReturn(true);
        when(authRepository.existsByEmail("new.player@example.com")).thenReturn(false);
        when(authRepository.existsByUsername("CoolPlayer")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123!")).thenReturn("hashed-password");
        when(authRepository.insert(
                "new.player@example.com",
                "CoolPlayer",
                "hashed-password",
                "2026-04-21",
                "2026-04-21"))
                .thenReturn(45L);

        AuthResponse response = authService.register(new RegisterRequest(
                "  New.Player@Example.COM ",
                " CoolPlayer ",
                "StrongPass123!",
                "2026-04-21",
                "2026-04-21"), null);

        assertThat(response.email()).isEqualTo("new.player@example.com");
        assertThat(response.username()).isEqualTo("CoolPlayer");
    }

    @Test
    @DisplayName("register with guest token merges the guest user instead of inserting a new user")
    void registerWithGuestTokenMergesGuestUser() {
        when(settingsService.isRegistrationEnabled()).thenReturn(true);
        when(authRepository.existsByEmail("new.player@example.com")).thenReturn(false);
        when(authRepository.existsByUsername("coolplayer")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123!")).thenReturn("hashed-password");
        when(authRepository.mergeGuestToRegistered(
                "guest-token",
                "new.player@example.com",
                "coolplayer",
                "hashed-password",
                "2026-04-21",
                "2026-04-21"))
                .thenReturn(Optional.of(88L));

        AuthResponse response = authService.register(new RegisterRequest(
                "new.player@example.com",
                "coolplayer",
                "StrongPass123!",
                "2026-04-21",
                "2026-04-21"), "guest-token");

        assertThat(response.username()).isEqualTo("coolplayer");
        assertThat(response.userId()).isEqualTo(88L);
        verify(authRepository, never()).insert(any(), any(), any(), any(), any());
        verify(legalAcceptanceService).recordUserAcceptance(
                88L, "2026-04-21", "2026-04-21", LegalAcceptanceService.SOURCE_REGISTER);
    }

    @Test
    @DisplayName("register rejects duplicate username with 409")
    void registerRejectsDuplicateUsername() {
        when(settingsService.isRegistrationEnabled()).thenReturn(true);
        when(authRepository.existsByEmail("new.player@example.com")).thenReturn(false);
        when(authRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "new.player@example.com",
                "taken",
                "StrongPass123!",
                "2026-04-21",
                "2026-04-21"), null))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    @DisplayName("completeOAuthRegistration with guest token merges the guest user")
    void completeOAuthRegistrationWithGuestTokenMergesGuestUser() {
        when(authRepository.existsByUsername("new_user")).thenReturn(false);
        when(authRepository.mergeGuestToOAuth(
                "guest-token",
                "oauth@example.com",
                "new_user",
                "github",
                "oauth-7",
                null,
                "2026-04-21",
                "2026-04-21"))
                .thenReturn(Optional.of(90L));

        AuthResponse response = authService.completeOAuthRegistration(
                "oauth@example.com",
                "new_user",
                "github",
                "oauth-7",
                null,
                "2026-04-21",
                "2026-04-21",
                "guest-token");

        assertThat(response.userId()).isEqualTo(90L);
        verify(authRepository, never()).insertOAuthUser(any(), any(), any(), any(), any(), any(), any());
        verify(legalAcceptanceService).recordUserAcceptance(
                90L, "2026-04-21", "2026-04-21", LegalAcceptanceService.SOURCE_OAUTH_REGISTER);
    }

    @Test
    @DisplayName("completeOAuthRegistration proxies remote avatar into MinIO and stores the public URL")
    void completeOAuthRegistrationProxiesAvatar() throws IOException {
        try (CloseTrackingInputStream avatarStream = new CloseTrackingInputStream(new byte[] {1, 2, 3})) {
            ProcessedAvatar avatar = new ProcessedAvatar(new byte[] {9, 8, 7, 6}, "image/png", "png");
            when(authRepository.existsByUsername("new_user")).thenReturn(false);
            when(authRepository.insertOAuthUser(
                    "oauth@example.com",
                    "new_user",
                    "github",
                    "oauth-7",
                    "https://avatars.githubusercontent.com/u/7",
                    "2026-04-21",
                    "2026-04-21"))
                    .thenReturn(7L);
            when(storageService.downloadFromUrl("https://avatars.githubusercontent.com/u/7"))
                    .thenReturn(avatarStream);
            when(avatarImageService.process(any(byte[].class))).thenReturn(avatar);
            when(storageService.upload(eq(7L), any(), anyLong(), eq("image/png"), eq("png")))
                    .thenReturn("https://cdn.example.com/avatars/7/avatar.png");

            AuthResponse response = authService.completeOAuthRegistration(
                    "oauth@example.com",
                    "new_user",
                    "github",
                    "oauth-7",
                    "https://avatars.githubusercontent.com/u/7",
                    "2026-04-21",
                    "2026-04-21",
                    null);

            verify(avatarImageService).process(any(byte[].class));
            verify(storageService).upload(eq(7L), any(), eq(4L), eq("image/png"), eq("png"));
            verify(profileRepository).updateAvatarUrl(7L, "https://cdn.example.com/avatars/7/avatar.png");
            assertThat(avatarStream.closed).isTrue();
            assertThat(jwtService.isAccessToken(response.accessToken())).isTrue();
            assertThat(response.refreshToken()).isEqualTo(ISSUED_REFRESH_TOKEN);
        }
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static Authentication authentication(UserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    private static UserPrincipal principal(Long id, String email, String role) {
        return new UserPrincipal(id, email, "hash", "user", role, true);
    }

    private static UserRow userRow(
            Long id, String email, String username, boolean totpEnabled, String totpSecret) {
        return new UserRow(
                id, email, "hash", username, "USER", true, totpSecret, totpEnabled, null, null, null, null, false, null);
    }
}
