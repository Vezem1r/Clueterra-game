package io.clueterra.api.profile.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeCountryProgressRow;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeBudgetCategoryProgressRow;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeGrantConfigRow;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeMissingCountryRow;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeProgressStatsRow;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProfileBadgeProgressJdbcRepository {

    private static final String FIND_GRANT_CONFIGS = SqlLoader.load(ProfileBadgeProgressJdbcRepository.class, "findGrantConfigs");
    private static final String LOAD_USER_STATS = SqlLoader.load(ProfileBadgeProgressJdbcRepository.class, "loadUserStats");
    private static final String LOAD_COUNTRY_PROGRESS = SqlLoader.load(ProfileBadgeProgressJdbcRepository.class, "loadCountryProgress");
    private static final String FIND_MISSING_COUNTRIES = SqlLoader.load(ProfileBadgeProgressJdbcRepository.class, "findMissingCountries");
    private static final String LOAD_BUDGET_CATEGORY_PROGRESS = SqlLoader.load(ProfileBadgeProgressJdbcRepository.class, "loadBudgetCategoryProgress");

    private final NamedParameterJdbcTemplate jdbc;

    public List<ProfileBadgeGrantConfigRow> findGrantConfigs(List<Long> badgeIds) {
        if (badgeIds == null || badgeIds.isEmpty()) {
            return List.of();
        }
        var params = new MapSqlParameterSource("badgeIds", badgeIds);
        return jdbc.query(FIND_GRANT_CONFIGS, params, (rs, rowNum) -> new ProfileBadgeGrantConfigRow(
                rs.getLong("id"),
                rs.getString("grant_type"),
                rs.getString("grant_config")));
    }

    public ProfileBadgeProgressStatsRow loadUserStats(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return jdbc.query(LOAD_USER_STATS, params, (rs, rowNum) -> new ProfileBadgeProgressStatsRow(
                rs.getLong("total_xp"),
                rs.getInt("current_level"),
                rs.getInt("challenges_played"),
                rs.getInt("challenges_won"),
                rs.getInt("current_streak"),
                rs.getInt("best_streak"),
                rs.getInt("perfect_runs"),
                rs.getLong("budget_completed_challenges"),
                rs.getLong("budget_exact_guesses"),
                rs.getLong("budget_clean_sweeps"),
                rs.getLong("budget_unique_projects"),
                rs.getLong("budget_best_higher_lower_streak"),
                rs.getLong("budget_higher_lower_correct"),
                rs.getLong("budget_cost_per_unit_correct"),
                rs.getLong("budget_distinct_countries"),
                rs.getLong("budget_mastered_value_usd"),
                rs.getLong("budget_severe_misses"),
                rs.getLong("river_attempted"),
                rs.getLong("river_solved"),
                rs.getLong("silhouette_solved"),
                rs.getLong("silhouette_total"),
                rs.getLong("flag_solved"),
                rs.getLong("flag_total"),
                rs.getLong("quiz_explored"),
                rs.getLong("quiz_total"))).stream().findFirst().orElse(null);
    }

    public ProfileBadgeBudgetCategoryProgressRow loadBudgetCategoryProgress(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return jdbc.query(LOAD_BUDGET_CATEGORY_PROGRESS, params, (rs, rowNum) ->
                new ProfileBadgeBudgetCategoryProgressRow(
                        rs.getInt("total_count"),
                        rs.getInt("mastered_count")))
                .stream()
                .findFirst()
                .orElse(new ProfileBadgeBudgetCategoryProgressRow(0, 0));
    }

    public ProfileBadgeCountryProgressRow loadCountryProgress(Long userId, String scopeType, String scopeValue) {
        var params = scopeParams(userId, scopeType, scopeValue);
        return jdbc.query(LOAD_COUNTRY_PROGRESS, params, (rs, rowNum) -> new ProfileBadgeCountryProgressRow(
                rs.getInt("total_count"),
                rs.getInt("completed_count"))).stream().findFirst().orElse(new ProfileBadgeCountryProgressRow(0, 0));
    }

    public List<ProfileBadgeMissingCountryRow> findMissingCountries(
            Long userId,
            String scopeType,
            String scopeValue,
            int limit) {
        var params = scopeParams(userId, scopeType, scopeValue)
                .addValue("limit", limit);
        return jdbc.query(FIND_MISSING_COUNTRIES, params, (rs, rowNum) -> new ProfileBadgeMissingCountryRow(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("flag"),
                rs.getString("flag_url")));
    }

    private MapSqlParameterSource scopeParams(Long userId, String scopeType, String scopeValue) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("scopeType", scopeType)
                .addValue("scopeValue", scopeValue);
    }
}
