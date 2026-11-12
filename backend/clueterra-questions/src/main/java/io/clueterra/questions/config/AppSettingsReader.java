package io.clueterra.questions.config;

import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppSettingsReader {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String COUNTRIES_PER_CHALLENGE_KEY = "countries_per_challenge";
    private static final String HINTS_PER_COUNTRY_KEY = "hints_per_country";
    private static final String AUTO_APPROVE_GENERATION_KEY = "auto_approve_generation";
    private static final String AUTO_APPROVE_REVIEW_KEY = "auto_approve_review";

    private static final int DEFAULT_COUNTRIES = 5;
    private static final int DEFAULT_HINTS = 5;

    private static final String GET_SETTING = """
            SELECT value FROM cl_api_schema.app_settings WHERE key = :key
            """;

    public int getCountriesPerChallenge() {
        return getIntSetting(COUNTRIES_PER_CHALLENGE_KEY, DEFAULT_COUNTRIES);
    }

    public int getHintsPerCountry() {
        return getIntSetting(HINTS_PER_COUNTRY_KEY, DEFAULT_HINTS);
    }

    public boolean isAutoApproveGeneration() {
        return getBooleanSetting(AUTO_APPROVE_GENERATION_KEY);
    }

    public boolean isAutoApproveReview() {
        return getBooleanSetting(AUTO_APPROVE_REVIEW_KEY);
    }

    private boolean getBooleanSetting(String key) {
        try {
            return jdbc.query(GET_SETTING, new MapSqlParameterSource("key", key), (rs, rowNum) -> rs.getString("value")).stream().findFirst().map(Boolean::parseBoolean).orElse(false);
        } catch (Exception e) {
            log.warn("Failed to read setting '{}', using default false", key, e);
            return false;
        }
    }

    private int getIntSetting(String key, int defaultValue) {
        try {
            Optional<String> value = jdbc.query(
                    GET_SETTING,
                    new MapSqlParameterSource("key", key),
                    (rs, rowNum) -> rs.getString("value")
            ).stream().findFirst();
            return value.map(Integer::parseInt).orElse(defaultValue);
        } catch (Exception e) {
            log.warn("Failed to read setting '{}', using default {}", key, defaultValue, e);
            return defaultValue;
        }
    }
}
