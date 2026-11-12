package io.clueterra.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.clueterra.api.auth.dto.AuthResponse;
import io.clueterra.api.auth.dto.PendingOAuthUser;
import io.clueterra.api.exception.InvalidCredentialsException;
import io.clueterra.api.exception.ResourceNotFoundException;

class OAuth2ServiceTest {

    private OAuth2Service service;

    @BeforeEach
    void setUp() {
        service = new OAuth2Service(mock(), mock(), mock());
        ReflectionTestUtils.setField(service, "pendingTtlMinutes", 10);
        ReflectionTestUtils.setField(service, "pendingMaxSize", 1000);
        ReflectionTestUtils.invokeMethod(service, "initCache");
    }

    @Test
    @DisplayName("auth codes are one-time tokens backed by authCodeCache")
    void authCodeCacheIsSingleUse() {
        AuthResponse tokens = new AuthResponse(
                "access", "refresh", "user@example.com", "user", "USER", 60_000L, false, false, 1L);

        String code = service.storeAuthCode(tokens);

        assertThat(service.exchangeAuthCode(code)).isEqualTo(tokens);
        assertThatThrownBy(() -> service.exchangeAuthCode(code))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("pending oauth users are resolved once and then invalidated")
    void pendingUsersAreSingleUse() {
        String token = service.storePendingUser(
                "pending@example.com", "Pending User", "https://avatar", "oauth-1", "github");

        PendingOAuthUser pending = service.resolvePendingUser(token);

        assertThat(pending.email()).isEqualTo("pending@example.com");
        assertThat(pending.provider()).isEqualTo("github");
        assertThatThrownBy(() -> service.resolvePendingUser(token))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("oauth state can be consumed only once")
    void stateIsSingleUse() {
        String state = service.generateAndStoreState();

        assertThat(service.validateAndConsumeState(state)).isTrue();
        assertThat(service.validateAndConsumeState(state)).isFalse();
    }
}
