package io.clueterra.api.config;

import org.jspecify.annotations.NonNull;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(@NonNull RuntimeException exception, Cache cache, @NonNull Object key) {
        log.warn("Ignoring cache read failure for cache={} key={}", cache.getName(), key, exception);
        cache.evictIfPresent(key);
    }

    @Override
    public void handleCachePutError(@NonNull RuntimeException exception, Cache cache, @NonNull Object key, Object value) {
        log.warn("Ignoring cache write failure for cache={} key={}", cache.getName(), key, exception);
    }

    @Override
    public void handleCacheEvictError(@NonNull RuntimeException exception, Cache cache, @NonNull Object key) {
        log.warn("Ignoring cache evict failure for cache={} key={}", cache.getName(), key, exception);
    }

    @Override
    public void handleCacheClearError(@NonNull RuntimeException exception, Cache cache) {
        log.warn("Ignoring cache clear failure for cache={}", cache.getName(), exception);
    }
}
