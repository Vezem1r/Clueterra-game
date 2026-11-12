package io.clueterra.api.common.utils.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
class RedisHelperIntegrationTest {

    private static final String KEY = "rl:test-counter";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private RedisHelper redisHelper;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redis.delete(KEY);
        redisHelper = new RedisHelper(redis, new ObjectMapper());
    }

    @Test
    @DisplayName("incrementInWindow counts within one window and keeps the first expiry")
    void countsWithinOneWindow() {
        RedisWindowCounter first = redisHelper.incrementInWindow(KEY, Duration.ofMinutes(15)).orElseThrow();
        RedisWindowCounter second = redisHelper.incrementInWindow(KEY, Duration.ofHours(1)).orElseThrow();

        assertThat(first.count()).isEqualTo(1);
        assertThat(second.count()).isEqualTo(2);
        assertThat(second.remaining()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("incrementInWindow restores a missing expiry instead of counting forever")
    void restoresMissingExpiry() {
        redis.opsForValue().set(KEY, "5");

        RedisWindowCounter counter = redisHelper.incrementInWindow(KEY, Duration.ofMinutes(15)).orElseThrow();

        assertThat(counter.count()).isEqualTo(6);
        assertThat(counter.remaining()).isEqualTo(Duration.ofMinutes(15));
        assertThat(redis.getExpire(KEY)).isPositive();
    }

    @Test
    @DisplayName("deleting the key starts a new window")
    void deleteStartsNewWindow() {
        redisHelper.incrementInWindow(KEY, Duration.ofMinutes(15));
        redisHelper.incrementInWindow(KEY, Duration.ofMinutes(15));

        redisHelper.delete(KEY);

        assertThat(redisHelper.incrementInWindow(KEY, Duration.ofMinutes(15)).orElseThrow().count()).isEqualTo(1);
    }
}
