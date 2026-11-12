package io.clueterra.api.admin.countries.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import io.clueterra.api.config.CacheConfig;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CountryCountsCacheService {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String COUNT_HINTS = """
            SELECT COUNT(*) FROM hints WHERE country_id = :countryId
            """;

    private static final String COUNT_STATISTICS = """
            SELECT COUNT(*) FROM country_statistics WHERE country_id = :countryId
            """;

    @Cacheable(cacheManager = "redisCacheManager", value = CacheConfig.COUNTRY_COUNTS_CACHE, key = "'hints:' + #countryId")
    public long getHintCount(Long countryId) {
        Long count = jdbc.queryForObject(COUNT_HINTS, new MapSqlParameterSource("countryId", countryId), Long.class);
        return count != null ? count : 0;
    }

    @Cacheable(cacheManager = "redisCacheManager", value = CacheConfig.COUNTRY_COUNTS_CACHE, key = "'stats:' + #countryId")
    public long getStatisticCount(Long countryId) {
        Long count = jdbc.queryForObject(COUNT_STATISTICS, new MapSqlParameterSource("countryId", countryId), Long.class);
        return count != null ? count : 0;
    }
}
