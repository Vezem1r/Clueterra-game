package io.clueterra.api.admin.indicators.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.clueterra.api.admin.indicators.dto.IndicatorConfigAdminDto;
import io.clueterra.api.admin.indicators.dto.IndicatorListCache;
import io.clueterra.api.common.utils.redis.RedisHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndicatorCacheService {

    private final RedisHelper redisHelper;

    private static final String P = "admin:indicators:";
    private static final Duration LIST_TTL = Duration.ofHours(2);
    private static final Duration META_TTL = Duration.ofHours(24);

    private static final TypeReference<IndicatorListCache> LIST_CACHE_REF = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_REF = new TypeReference<>() {
    };

    public String listKey(String kind, int page, int size, String search, String category,
                          String profileFacet, Boolean isActive, String sortBy, String sortDir) {
        return P + n(kind) + ":list:" + page + ":" + size
                + ":" + n(search) + ":" + n(category) + ":" + n(profileFacet)
                + ":" + isActive + ":" + n(sortBy) + ":" + n(sortDir);
    }

    public Optional<Page<IndicatorConfigAdminDto>> getList(String key, Pageable pageable) {
        return redisHelper.read(key, LIST_CACHE_REF)
                .map(c -> new PageImpl<>(c.content(), pageable, c.totalElements()));
    }

    public void putList(String key, Page<IndicatorConfigAdminDto> page) {
        redisHelper.write(key, new IndicatorListCache(page.getContent(), page.getTotalElements()), LIST_TTL);
    }

    public Optional<List<String>> getStringList(String key) {
        return redisHelper.read(key, STRING_LIST_REF);
    }

    public void putMeta(String key, Object value) {
        redisHelper.write(key, value, META_TTL);
    }

    public String categoriesKey(String kind) {
        return P + n(kind) + ":categories";
    }

    public String profileLevelsKey(String kind) {
        return P + n(kind) + ":profile-levels";
    }

    public long evictAll() {
        Set<String> keys = redisHelper.keys(P + "*");
        if (keys == null || keys.isEmpty()) return 0;
        long count = keys.size();
        redisHelper.delete(keys);
        log.info("Indicator cache: evicted all {} keys", count);
        return count;
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
