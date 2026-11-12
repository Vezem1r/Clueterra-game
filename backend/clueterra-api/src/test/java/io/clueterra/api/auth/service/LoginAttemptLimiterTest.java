package io.clueterra.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.api.common.utils.redis.RedisHelper;
import io.clueterra.api.common.utils.redis.RedisWindowCounter;
import io.clueterra.api.config.RateLimitingProperties;
import io.clueterra.api.exception.TooManyLoginAttemptsException;
import io.clueterra.api.security.RateLimitKeyHasher;

@ExtendWith(MockitoExtension.class)
class LoginAttemptLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Mock
    private RedisHelper redisHelper;

    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        RateLimitingProperties properties = new RateLimitingProperties();
        properties.setAuthLoginAttemptsPerAccount(10);
        properties.setAuthLoginAccountWindow(WINDOW);
        properties.setKeySalt("test-salt");
        limiter = new LoginAttemptLimiter(redisHelper, properties, new RateLimitKeyHasher(properties));
    }

    @Test
    @DisplayName("attempts up to the account limit are allowed")
    void allowsAttemptsUpToLimit() {
        when(redisHelper.incrementInWindow(anyString(), eq(WINDOW)))
                .thenReturn(Optional.of(new RedisWindowCounter(10, Duration.ofMinutes(5))));

        assertThatCode(() -> limiter.acquire("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the attempt after the limit is rejected until the window expires")
    void rejectsAttemptAfterLimit() {
        when(redisHelper.incrementInWindow(anyString(), eq(WINDOW)))
                .thenReturn(Optional.of(new RedisWindowCounter(11, Duration.ofMillis(90_500))));

        assertThatThrownBy(() -> limiter.acquire("user@example.com"))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(91));
    }

    @Test
    @DisplayName("an unavailable Redis does not block logins")
    void allowsAttemptWhenRedisIsUnavailable() {
        when(redisHelper.incrementInWindow(anyString(), eq(WINDOW))).thenReturn(Optional.empty());

        assertThatCode(() -> limiter.acquire("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the counter key is a salted hash and never contains the email")
    void keyIsHashedAndSharedByAcquireAndReset() {
        when(redisHelper.incrementInWindow(anyString(), eq(WINDOW))).thenReturn(Optional.empty());

        limiter.acquire("user@example.com");
        limiter.reset("user@example.com");

        ArgumentCaptor<String> acquiredKey = ArgumentCaptor.forClass(String.class);
        verify(redisHelper).incrementInWindow(acquiredKey.capture(), eq(WINDOW));
        verify(redisHelper).delete(acquiredKey.getValue());
        assertThat(acquiredKey.getValue())
                .startsWith("rl:auth-account:")
                .doesNotContain("user")
                .doesNotContain("example");
    }
}
