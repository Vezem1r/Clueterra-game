package io.clueterra.api.auth.service;

import org.springframework.stereotype.Service;

import io.clueterra.api.common.utils.redis.RedisHelper;
import io.clueterra.api.common.utils.redis.RedisWindowCounter;
import io.clueterra.api.config.RateLimitingProperties;
import io.clueterra.api.exception.TooManyLoginAttemptsException;
import io.clueterra.api.security.RateLimitKeyHasher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptLimiter {

    private static final String KEY_PREFIX = "rl:auth-account:";

    private final RedisHelper redisHelper;
    private final RateLimitingProperties properties;
    private final RateLimitKeyHasher keyHasher;

    public void acquire(String email) {
        String key = key(email);
        redisHelper.incrementInWindow(key, properties.getAuthLoginAccountWindow())
                .filter(counter -> counter.count() > properties.getAuthLoginAttemptsPerAccount())
                .ifPresent(counter -> {
                    log.warn("Login attempts exceeded for account key {}", key);
                    throw new TooManyLoginAttemptsException(retryAfterSeconds(counter));
                });
    }

    public void reset(String email) {
        redisHelper.delete(key(email));
    }

    private String key(String email) {
        return KEY_PREFIX + keyHasher.hash(email);
    }

    private static long retryAfterSeconds(RedisWindowCounter counter) {
        return Math.max(1L, (counter.remaining().toMillis() + 999) / 1000);
    }
}
