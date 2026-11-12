package io.clueterra.budget.common.redis;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisHelper {

    private static final RedisScript<Long> RENEW_LEASE_SCRIPT =
            RedisScript.of(
                    """
                    if redis.call('get', KEYS[1]) ~= ARGV[1] then
                        return 0
                    end
                    redis.call('pexpire', KEYS[1], ARGV[2])
                    if redis.call('exists', KEYS[2]) == 1 then
                        redis.call('pexpire', KEYS[2], ARGV[2])
                    end
                    return 1
                    """,
                    Long.class);
    private static final RedisScript<Long> DELETE_IF_VALUE_MATCHES_SCRIPT =
            RedisScript.of(
                    """
                    if redis.call('get', KEYS[1]) ~= ARGV[1] then
                        return 0
                    end
                    return redis.call('del', KEYS[1])
                    """,
                    Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> read(String key, Class<T> type) {
        Optional<String> json = readRaw(key);
        if (json.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json.get(), type));
        } catch (Exception exception) {
            log.warn("Redis value is invalid and will be deleted [{}]: {}", key, exception.getMessage());
            delete(key);
            return Optional.empty();
        }
    }

    public <T> Optional<T> read(String key, TypeReference<T> type) {
        Optional<String> json = readRaw(key);
        if (json.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json.get(), type));
        } catch (Exception exception) {
            log.warn("Redis value is invalid and will be deleted [{}]: {}", key, exception.getMessage());
            delete(key);
            return Optional.empty();
        }
    }

    public void write(String key, Object value, Duration ttl) {
        try {
            writeRequired(key, value, ttl);
        } catch (Exception exception) {
            log.warn("Redis write failed [{}]: {}", key, exception.getMessage());
        }
    }

    public void writeRequired(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception exception) {
            throw new IllegalStateException("Redis write failed for key " + key, exception);
        }
    }

    public boolean writeIfAbsent(String key, Object value, Duration ttl) {
        try {
            Boolean written = redis.opsForValue().setIfAbsent(key, objectMapper.writeValueAsString(value), ttl);
            return Boolean.TRUE.equals(written);
        } catch (Exception exception) {
            throw new IllegalStateException("Redis coordination failed for key " + key, exception);
        }
    }

    public boolean renewLease(
            String lockKey, String companionKey, Object expectedValue, Duration ttl) {
        try {
            Long renewed =
                    redis.execute(
                            RENEW_LEASE_SCRIPT,
                            List.of(lockKey, companionKey),
                            objectMapper.writeValueAsString(expectedValue),
                            Long.toString(ttl.toMillis()));
            return Long.valueOf(1L).equals(renewed);
        } catch (Exception exception) {
            throw new IllegalStateException("Redis lease renewal failed for key " + lockKey, exception);
        }
    }

    public void deleteIfValueMatches(String key, Object expectedValue) {
        try {
            redis.execute(
                    DELETE_IF_VALUE_MATCHES_SCRIPT,
                    List.of(key),
                    objectMapper.writeValueAsString(expectedValue));
        } catch (Exception exception) {
            throw new IllegalStateException("Redis coordinated delete failed for key " + key, exception);
        }
    }

    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Redis delete failed [{}]: {}", key, exception.getMessage());
        }
    }

    private Optional<String> readRaw(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (RuntimeException exception) {
            log.warn("Redis read failed [{}]: {}", key, exception.getMessage());
            return Optional.empty();
        }
    }
}
