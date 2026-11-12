package io.clueterra.api.admin.hints.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.clueterra.api.common.utils.redis.RedisHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import io.clueterra.api.admin.hints.dto.HintAdminDto;
import io.clueterra.api.admin.hints.dto.HintListCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HintCacheService {

    private final RedisHelper redisHelper;

    private static final String P = "admin:hints:";
    private static final Duration LIST_TTL = Duration.ofMinutes(120);
    private static final Duration META_TTL = Duration.ofHours(24);

    private static final TypeReference<HintListCache> LIST_CACHE_REF = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_REF = new TypeReference<>() {
    };
    private static final TypeReference<List<Integer>> INT_LIST_REF = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {
    };

    public Optional<Page<HintAdminDto>> getList(String key, Pageable pageable) {
        return redisHelper.read(key, LIST_CACHE_REF).map(c -> new PageImpl<>(c.content(), pageable, c.totalElements()));
    }

    public void putList(String key, Page<HintAdminDto> page) {
        redisHelper.write(key, new HintListCache(page.getContent(), page.getTotalElements()), LIST_TTL);
    }

    public String listKey(int page, int size, String search, List<Long> countryIds,
                          String category, String hintType, String source, Boolean active,
                          Integer diffMin, Integer diffMax, Integer sourceYear, Boolean hasImage,
                          String sortBy, String sortDir) {
        return P + "list:" + page + ":" + size
                + ":" + n(search)
                + ":" + (countryIds == null || countryIds.isEmpty() ? ""
                : countryIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")))
                + ":" + n(category) + ":" + n(hintType) + ":" + n(source)
                + ":" + active
                + ":" + diffMin + ":" + diffMax + ":" + sourceYear
                + ":" + hasImage
                + ":" + n(sortBy) + ":" + n(sortDir);
    }

    public Optional<List<String>> getStringList(String key) {
        return redisHelper.read(key, STRING_LIST_REF);
    }

    public Optional<List<Integer>> getIntList(String key) {
        return redisHelper.read(key, INT_LIST_REF);
    }

    public Optional<Map<String, Object>> getMap(String key) {
        return redisHelper.read(key, MAP_REF);
    }

    public void putMeta(String key, Object value) {
        redisHelper.write(key, value, META_TTL);
    }

    public String categoriesKey() {
        return P + "categories";
    }

    public String typesKey() {
        return P + "types";
    }

    public String sourcesKey() {
        return P + "sources";
    }

    public String yearsKey() {
        return P + "years";
    }

    public String statsKey() {
        return P + "stats";
    }

    public void evictOnMutation() {
        long n = redisHelper.evictPattern(P + "list:*");
        redisHelper.delete(P + "stats");
        log.info("Hint cache: evicted {} list keys + stats", n);
    }

    public long evictAll() {
        Set<String> keys = redisHelper.keys(P + "*");
        if (keys == null || keys.isEmpty()) return 0;
        long count = keys.size();
        redisHelper.delete(keys);
        log.info("Hint cache: evicted all {} keys", count);
        return count;
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
