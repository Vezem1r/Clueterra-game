package io.clueterra.api.admin.challenges.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.clueterra.api.common.utils.redis.RedisHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import io.clueterra.api.admin.challenges.dto.ChallengeAdminDto;
import io.clueterra.api.admin.challenges.dto.ChallengeListCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChallengeCacheService {

    private final RedisHelper redisHelper;

    private static final String P = "admin:challenges:";
    private static final String GAME_TODAY_PREFIX = "challenge:today:";
    private static final Duration LIST_TTL = Duration.ofMinutes(30);
    private static final Duration META_TTL = Duration.ofHours(2);

    private static final TypeReference<ChallengeListCache> LIST_CACHE_REF = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_REF = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {
    };

    public Optional<Page<ChallengeAdminDto>> getList(String key, Pageable pageable) {
        return redisHelper.read(key, LIST_CACHE_REF).map(c -> new PageImpl<>(c.content(), pageable, c.totalElements()));
    }

    public void putList(String key, Page<ChallengeAdminDto> page) {
        redisHelper.write(key, new ChallengeListCache(page.getContent(), page.getTotalElements()), LIST_TTL);
    }

    public String listKey(int page, int size, LocalDate startDate, LocalDate endDate,
                          String level, String difficulty, Boolean active,
                          String sortBy, String sortDir) {
        return P + "list:" + page + ":" + size
                + ":" + (startDate != null ? startDate : "")
                + ":" + (endDate != null ? endDate : "")
                + ":" + n(level) + ":" + n(difficulty) + ":" + active
                + ":" + n(sortBy) + ":" + n(sortDir);
    }

    public Optional<List<String>> getStringList(String key) {
        return redisHelper.read(key, STRING_LIST_REF);
    }

    public Optional<Map<String, Object>> getMap(String key) {
        return redisHelper.read(key, MAP_REF);
    }

    public void putMeta(String key, Object value) {
        redisHelper.write(key, value, META_TTL);
    }

    public String levelsKey() {
        return P + "levels";
    }

    public String difficultiesKey() {
        return P + "difficulties";
    }

    public String statsKey() {
        return P + "stats";
    }

    public void evictOnMutation() {
        long n = redisHelper.evictPattern(P + "list:*");
        redisHelper.delete(P + "stats");
        log.info("Challenge cache: evicted {} list keys + stats", n);
    }

    public long evictTodayGameplayCache(LocalDate today) {
        long n = redisHelper.evictPattern(GAME_TODAY_PREFIX + today + ":*");
        log.info("Challenge cache: evicted {} gameplay keys for {}", n, today);
        return n;
    }

    public long evictAll() {
        Set<String> keys = redisHelper.keys(P + "*");
        if (keys == null || keys.isEmpty()) return 0;
        long count = keys.size();
        redisHelper.delete(keys);
        log.info("Challenge cache: evicted all {} keys", count);
        return count;
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
