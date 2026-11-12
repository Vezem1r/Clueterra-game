package io.clueterra.api.auth.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.auth.dto.AuthResponse;
import io.clueterra.api.auth.dto.LoginRequest;
import io.clueterra.api.auth.dto.RegisterRequest;
import io.clueterra.api.auth.dto.TotpLoginRequest;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.config.JwtProperties;
import io.clueterra.api.config.TotpProperties;
import io.clueterra.api.exception.EmailAlreadyExistsException;
import io.clueterra.api.exception.InvalidCredentialsException;
import io.clueterra.api.exception.RegistrationDisabledException;
import io.clueterra.api.exception.ResourceConflictException;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.profile.service.AvatarImageService;
import io.clueterra.api.profile.service.ProcessedAvatar;
import io.clueterra.api.profile.repository.ProfileJdbcRepository;
import io.clueterra.api.security.UserPrincipal;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String ACCESS_COOKIE = "clueterra_access";
    private static final String REFRESH_COOKIE = "clueterra_refresh";
    private static final String GUEST_COOKIE = "ct_guest";
    private static final long GUEST_COOKIE_MAX_AGE_SECONDS = 30L * 24 * 60 * 60;

    private final AuthJdbcRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final TotpProperties totpProperties;
    private final AuthenticationManager authenticationManager;
    private final SettingsService settingsService;
    private final TotpService totpService;
    private final StorageService storageService;
    private final ProfileJdbcRepository profileRepository;
    private final LegalAcceptanceService legalAcceptanceService;
    private final GuestProgressClaimService guestProgressClaimService;
    private final AvatarImageService avatarImageService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    @Transactional
    public AuthResponse register(RegisterRequest request, String guestToken) {
        if (!settingsService.isRegistrationEnabled()) {
            throw new RegistrationDisabledException();
        }

        String email = IdentityNormalizer.normalizeEmail(request.email());
        String username = IdentityNormalizer.normalizeUsername(request.username());

        if (authRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        if (authRepository.existsByUsername(username)) {
            throw new ResourceConflictException("Username is already taken");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        long userId;

        if (hasText(guestToken)) {
            Optional<Long> mergedUserId;
            try {
                mergedUserId = authRepository.mergeGuestToRegistered(
                        guestToken, email, username, passwordHash,
                        request.termsVersion(), request.privacyVersion());
            } catch (DuplicateKeyException exception) {
                throw identityConflict(exception);
            }
            if (mergedUserId.isPresent()) {
                userId = mergedUserId.get();
                legalAcceptanceService.recordUserAcceptance(userId, request.termsVersion(), request.privacyVersion(), LegalAcceptanceService.SOURCE_REGISTER);
                guestProgressClaimService.finalizeConvertedBudgetProgress(userId);
                log.info("Guest merged to registered user: userId={}", userId);

                UserPrincipal principal = new UserPrincipal(userId, email, passwordHash, username, "USER", true, request.termsVersion(), request.privacyVersion());
                return createAuthResponse(principal);
            }
            log.warn("Guest merge to registered user failed: token expired or already merged");
        }

        try {
            userId = authRepository.insert(email, username, passwordHash, request.termsVersion(), request.privacyVersion());
        } catch (DuplicateKeyException e) {
            throw identityConflict(e);
        }
        legalAcceptanceService.recordUserAcceptance(userId, request.termsVersion(), request.privacyVersion(), LegalAcceptanceService.SOURCE_REGISTER);

        log.info("New user registered: userId={}", userId);

        UserPrincipal principal = new UserPrincipal(
                userId, email, passwordHash, username, "USER", true,
                request.termsVersion(), request.privacyVersion());
        return createAuthResponse(principal);
    }

    public AuthResponse login(LoginRequest request, String guestToken) {
        String email = IdentityNormalizer.normalizeEmail(request.email());
        loginAttemptLimiter.acquire(email);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

            UserRow user = authRepository.findByEmail(email)
                    .orElseThrow(InvalidCredentialsException::new);

            if (totpProperties.isEnabled() && user.totpEnabled()) {
                String role = principal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
                return new AuthResponse(
                        null,
                        null,
                        principal.getEmail(),
                        user.username(),
                        role,
                        0,
                        true,
                        legalAcceptanceService.requiresAcceptance(principal),
                        null);
            }

            claimGuestProgress(guestToken, principal.getId());
            AuthResponse response = createAuthResponse(principal);
            loginAttemptLimiter.reset(email);
            return response;
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException();
        }
    }

    public AuthResponse verifyTotp(TotpLoginRequest request, String guestToken) {
        String email = IdentityNormalizer.normalizeEmail(request.email());
        loginAttemptLimiter.acquire(email);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

            UserRow user = authRepository.findByEmail(email)
                    .orElseThrow(InvalidCredentialsException::new);

            if (!user.totpEnabled() || user.totpSecret() == null) {
                throw new InvalidCredentialsException();
            }

            if (totpService.isCodeInvalid(user.id(), user.totpSecret(), request.totpCode())) {
                throw new InvalidCredentialsException();
            }

            claimGuestProgress(guestToken, principal.getId());
            AuthResponse response = createAuthResponse(principal);
            loginAttemptLimiter.reset(email);
            return response;
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException();
        }
    }

    public AuthResponse refresh(String refreshToken) {
        RotatedRefreshToken rotated = refreshTokenService.rotate(refreshToken)
                .orElseThrow(InvalidCredentialsException::new);
        UserRow user = authRepository.findById(rotated.userId()).orElseThrow(InvalidCredentialsException::new);
        if (!user.enabled()) {
            refreshTokenService.revokeAll(user.id());
            throw new InvalidCredentialsException();
        }

        UserPrincipal principal = new UserPrincipal(
                user.id(), user.email(), user.passwordHash(), user.username(), user.role(), true,
                user.termsVersionAccepted(), user.privacyVersionAccepted());
        return createAuthResponse(principal, rotated.token());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional
    public AuthResponse completeOAuthRegistration(String email, String username, String oauthProvider,
                                                  String oauthId, String avatarUrl, String termsVersion,
                                                  String privacyVersion, String guestToken) {
        email = IdentityNormalizer.normalizeEmail(email);
        username = IdentityNormalizer.normalizeUsername(username);
        if (authRepository.existsByUsername(username)) {
            throw new ResourceConflictException("Username is already taken");
        }

        long userId;

        if (hasText(guestToken)) {
            Optional<Long> mergedUserId;
            try {
                mergedUserId = authRepository.mergeGuestToOAuth(
                        guestToken, email, username, oauthProvider, oauthId, avatarUrl,
                        termsVersion, privacyVersion);
            } catch (DuplicateKeyException exception) {
                throw identityConflict(exception);
            }
            if (mergedUserId.isPresent()) {
                userId = mergedUserId.get();
                legalAcceptanceService.recordUserAcceptance(userId, termsVersion, privacyVersion, LegalAcceptanceService.SOURCE_OAUTH_REGISTER);
                guestProgressClaimService.finalizeConvertedBudgetProgress(userId);
                proxyAvatarToStorage(userId, avatarUrl);
                log.info("Guest merged to OAuth user: userId={}, provider={}", userId, oauthProvider);

                UserPrincipal principal = new UserPrincipal(userId, email, null, username, "USER", true, termsVersion, privacyVersion);
                return createAuthResponse(principal);
            }
            log.warn("Guest merge to OAuth failed: token expired or already merged, provider={}", oauthProvider);
        }

        try {
            userId = authRepository.insertOAuthUser(email, username, oauthProvider, oauthId, avatarUrl, termsVersion, privacyVersion);
        } catch (DuplicateKeyException e) {
            throw identityConflict(e);
        }
        legalAcceptanceService.recordUserAcceptance(userId, termsVersion, privacyVersion, LegalAcceptanceService.SOURCE_OAUTH_REGISTER);

        proxyAvatarToStorage(userId, avatarUrl);

        log.info("OAuth user registered: userId={}, provider={}", userId, oauthProvider);

        UserPrincipal principal = new UserPrincipal(
                userId, email, null, username, "USER", true, termsVersion, privacyVersion);
        return createAuthResponse(principal);
    }

    private void proxyAvatarToStorage(long userId, String avatarUrl) {
        if (!hasText(avatarUrl)) return;
        try (InputStream stream = storageService.downloadFromUrl(avatarUrl)) {
            if (stream == null) return;
            ProcessedAvatar avatar = avatarImageService.process(stream.readAllBytes());
            String minioUrl = storageService.upload(
                    userId,
                    new ByteArrayInputStream(avatar.data()),
                    avatar.data().length,
                    avatar.contentType(),
                    avatar.extension());
            profileRepository.updateAvatarUrl(userId, minioUrl);
        } catch (Exception e) {
            log.error("Failed to proxy OAuth avatar to storage for userId={}", userId, e);
        }
    }

    public AuthResponse createTokensForPrincipal(UserPrincipal principal) {
        return createAuthResponse(principal);
    }

    public void claimGuestProgress(String guestToken, Long targetUserId) {
        guestProgressClaimService.claimTodayProgress(guestToken, targetUserId);
    }

    public ResponseCookie buildAccessCookie(String token, long maxAgeMs) {
        return ResponseCookie.from(ACCESS_COOKIE, token)
                .httpOnly(true).secure(true).sameSite("Lax")
                .path("/api").maxAge(Duration.ofMillis(maxAgeMs)).build();
    }

    public ResponseCookie buildRefreshCookie(String token, long maxAgeMs) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true).secure(true).sameSite("Lax")
                .path("/api/auth/refresh").maxAge(Duration.ofMillis(maxAgeMs)).build();
    }

    public ResponseCookie clearAccessCookie() {
        return ResponseCookie.from(ACCESS_COOKIE, "")
                .httpOnly(true).secure(true).sameSite("Lax")
                .path("/api").maxAge(0).build();
    }

    public ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(true).sameSite("Lax")
                .path("/api/auth/refresh").maxAge(0).build();
    }

    public ResponseCookie buildGuestCookie(String token) {
        return ResponseCookie.from(GUEST_COOKIE, token)
                .httpOnly(true).secure(true).sameSite("Lax")
                .path("/api").maxAge(Duration.ofSeconds(GUEST_COOKIE_MAX_AGE_SECONDS)).build();
    }

    public ResponseCookie clearGuestCookie() {
        return ResponseCookie.from(GUEST_COOKIE, "")
                .httpOnly(true).secure(true).sameSite("Lax")
                .path("/api").maxAge(0).build();
    }

    private AuthResponse createAuthResponse(UserPrincipal principal) {
        if (!principal.isEnabled()) {
            throw new InvalidCredentialsException();
        }
        return createAuthResponse(principal, refreshTokenService.issue(principal.getId()));
    }

    private AuthResponse createAuthResponse(UserPrincipal principal, String refreshToken) {
        String accessToken = jwtService.generateToken(principal);
        String role = principal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");

        return new AuthResponse(accessToken, refreshToken, principal.getEmail(),
                principal.getDisplayUsername(), role, jwtProperties.getExpiration(), false,
                legalAcceptanceService.requiresAcceptance(principal), principal.getId());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private RuntimeException identityConflict(DuplicateKeyException exception) {
        String message = String.valueOf(exception.getMostSpecificCause().getMessage())
                .toLowerCase(Locale.ROOT);
        if (message.contains("username")) {
            return new ResourceConflictException("Username is already taken");
        }
        if (message.contains("email")) {
            return new EmailAlreadyExistsException();
        }
        return new ResourceConflictException("Email or username is already taken");
    }

}
