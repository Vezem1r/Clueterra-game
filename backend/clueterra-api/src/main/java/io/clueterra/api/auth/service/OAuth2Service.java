package io.clueterra.api.auth.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.clueterra.api.auth.dto.AuthResponse;
import io.clueterra.api.auth.dto.PendingOAuthUser;
import io.clueterra.api.auth.dto.OAuthUserInfo;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.exception.InvalidCredentialsException;
import io.clueterra.api.exception.ResourceNotFoundException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2Service {

    private final AuthJdbcRepository authRepository;
    private final GitHubOAuthClient githubClient;
    private final GoogleOAuthClient googleClient;

    @Value("${clueterra.oauth.pending-ttl-minutes:10}")
    private int pendingTtlMinutes;

    @Value("${clueterra.oauth.pending-max-size:1000}")
    private int pendingMaxSize;

    private Cache<String, PendingOAuthUser> pendingUsers;
    private Cache<String, Boolean> oauthStates;
    private Cache<String, AuthResponse> authCodeCache;

    @PostConstruct
    void initCache() {
        pendingUsers = Caffeine.newBuilder()
                .expireAfterWrite(pendingTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(pendingMaxSize)
                .build();
        oauthStates = Caffeine.newBuilder()
                .expireAfterWrite(pendingTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(pendingMaxSize)
                .build();
        authCodeCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
    }

    public String generateAndStoreState() {
        String state = UUID.randomUUID().toString();
        oauthStates.put(state, Boolean.TRUE);
        return state;
    }

    public boolean validateAndConsumeState(String state) {
        if (state == null) return false;
        Boolean valid = oauthStates.getIfPresent(state);
        oauthStates.invalidate(state);
        return Boolean.TRUE.equals(valid);
    }

    public String getAuthorizationUrl(String provider, String callbackUrl, String state) {
        return switch (provider) {
            case "github" -> githubClient.buildAuthUrl(callbackUrl, state);
            case "google" -> googleClient.buildAuthUrl(callbackUrl, state);
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    public OAuthUserInfo exchangeCodeForUserInfo(String provider, String code, String callbackUrl) {
        try {
            OAuthUserInfo userInfo = switch (provider) {
                case "github" -> githubClient.getUserInfo(code, callbackUrl);
                case "google" -> googleClient.getUserInfo(code, callbackUrl);
                default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
            };
            log.info("OAuth code exchanged: provider={}", provider);
            return userInfo;
        } catch (Exception e) {
            log.error("Failed to exchange OAuth code for userInfo: provider={}", provider, e);
            throw e;
        }
    }

    public Optional<UserRow> findExistingOAuthUser(String provider, String oauthId) {
        return authRepository.findByOAuth(provider, oauthId);
    }

    public Optional<UserRow> findExistingUserByEmail(String email) {
        return authRepository.findByEmail(IdentityNormalizer.normalizeEmail(email));
    }

    public String storePendingUser(String email, String name, String avatarUrl, String oauthId, String provider) {
        String token = UUID.randomUUID().toString();
        pendingUsers.put(token, new PendingOAuthUser(
                IdentityNormalizer.normalizeEmail(email), name, avatarUrl, oauthId, provider));
        return token;
    }

    public PendingOAuthUser resolvePendingUser(String pendingToken) {
        PendingOAuthUser pending = pendingUsers.getIfPresent(pendingToken);
        if (pending == null) {
            throw new ResourceNotFoundException("Invalid or expired pending token");
        }
        pendingUsers.invalidate(pendingToken);
        return pending;
    }

    public String storeAuthCode(AuthResponse tokens) {
        String code = UUID.randomUUID().toString();
        authCodeCache.put(code, tokens);
        return code;
    }

    public AuthResponse exchangeAuthCode(String code) {
        AuthResponse tokens = authCodeCache.getIfPresent(code);
        if (tokens == null) {
            throw new InvalidCredentialsException();
        }
        authCodeCache.invalidate(code);
        return tokens;
    }
}
