package io.clueterra.api.common.utils.redis;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> INCREMENT_IN_WINDOW = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public <T> Optional<T> read(String key, TypeReference<T> ref) {
        Optional<String> json = readRaw(key);
        if (json.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json.get(), ref));
        } catch (Exception e) {
            log.warn("Redis value is invalid and will be deleted [{}]: {}", key, e.getMessage());
            delete(key);
            return Optional.empty();
        }
    }

    public <T> Optional<T> read(String key, Class<T> type) {
        Optional<String> json = readRaw(key);
        if (json.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json.get(), type));
        } catch (Exception e) {
            log.warn("Redis value is invalid and will be deleted [{}]: {}", key, e.getMessage());
            delete(key);
            return Optional.empty();
        }
    }

    public void write(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Redis cache write failed [{}]: {}", key, e.getMessage());
        }
    }

    public Optional<RedisWindowCounter> incrementInWindow(String key, Duration window) {
        try {
            List<?> result = redis.execute(INCREMENT_IN_WINDOW, List.of(key), String.valueOf(window.toMillis()));
            if (result.size() != 2) {
                return Optional.empty();
            }
            long count = ((Number) result.get(0)).longValue();
            Duration remaining = Duration.ofMillis(((Number) result.get(1)).longValue());
            return Optional.of(new RedisWindowCounter(count, remaining));
        } catch (RuntimeException e) {
            log.warn("Redis counter increment failed [{}]: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public long evictPattern(String pattern) {
        try {
            Set<String> keys = redis.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            redis.delete(keys);
            return keys.size();
        } catch (RuntimeException e) {
            log.warn("Redis pattern eviction failed [{}]: {}", pattern, e.getMessage());
            return 0;
        }
    }

    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("Redis delete failed [{}]: {}", key, e.getMessage());
        }
    }

    public void delete(Set<String> keys) {
        try {
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (RuntimeException e) {
            log.warn("Redis bulk delete failed: {}", e.getMessage());
        }
    }

    public Set<String> keys(String pattern) {
        try {
            return redis.keys(pattern);
        } catch (RuntimeException e) {
            log.warn("Redis key lookup failed [{}]: {}", pattern, e.getMessage());
            return Set.of();
        }
    }

    private Optional<String> readRaw(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (RuntimeException e) {
            log.warn("Redis read failed [{}]: {}", key, e.getMessage());
            return Optional.empty();
        }
    }
}
