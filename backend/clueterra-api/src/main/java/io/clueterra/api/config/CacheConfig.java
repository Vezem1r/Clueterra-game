package io.clueterra.api.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.clueterra.api.friends.dto.FriendLeaderboardEntryDto;
import io.clueterra.api.leaderboard.dto.LeaderboardPageResponse;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String COUNTRY_COUNTS_CACHE = "countryCounts";
    public static final String LEADERBOARD_CACHE = "leaderboard";
    public static final String FRIENDS_LEADERBOARD_CACHE = "friendsLeaderboard";

    @Primary
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .recordStats());
        return cacheManager;
    }

    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory factory, ObjectMapper objectMapper) {
        GenericJackson2JsonRedisSerializer valueSerializer =
                GenericJackson2JsonRedisSerializer.builder()
                        .objectMapper(objectMapper.copy())
                        .defaultTyping(true)
                        .build();

        RedisCacheConfiguration defaultConfig = cacheConfig(Duration.ofHours(1), valueSerializer);

        Map<String, RedisCacheConfiguration> perCacheConfig = Map.of(
                COUNTRY_COUNTS_CACHE, cacheConfig(Duration.ofHours(1), typedSerializer(objectMapper, Long.class)),
                LEADERBOARD_CACHE, cacheConfig(Duration.ofMinutes(5), typedSerializer(objectMapper, LeaderboardPageResponse.class)),
                FRIENDS_LEADERBOARD_CACHE,
                cacheConfig(Duration.ofMinutes(5), listSerializer(objectMapper, FriendLeaderboardEntryDto.class)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .build();
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new RedisCacheErrorHandler();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return cacheErrorHandler();
    }

    private RedisCacheConfiguration cacheConfig(Duration ttl, RedisSerializer<?> valueSerializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
    }

    private RedisSerializer<?> typedSerializer(ObjectMapper objectMapper, Class<?> valueType) {
        return new Jackson2JsonRedisSerializer<>(objectMapper.copy(), valueType);
    }

    private RedisSerializer<?> listSerializer(ObjectMapper objectMapper, Class<?> elementType) {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return new Jackson2JsonRedisSerializer<>(objectMapper.copy(), listType);
    }
}
