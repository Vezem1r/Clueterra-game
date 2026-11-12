package io.clueterra.api.achievements.engine;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;
import io.hypersistence.tsid.TSID;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BadgeEngineJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String FIND_UNCLAIMED_TEMPLATES =
            SqlLoader.load(BadgeEngineJdbcRepository.class, "findUnclaimedTemplates");

    private static final String LOAD_USER_STATS =
            SqlLoader.load(BadgeEngineJdbcRepository.class, "loadUserStats");

    private static final String GRANT_BADGE_WITH_SHIELD = """
            WITH badge_grant AS (
                INSERT INTO user_badges (id, user_id, badge_id, granted_by, is_acknowledged)
                VALUES (:id, :userId, :badgeId, 'SYSTEM', FALSE)
                ON CONFLICT (user_id, badge_id) DO NOTHING
                RETURNING user_id
            )
            UPDATE user_profiles
            SET streak_freezes = LEAST(streak_freezes + :freezePerBadge, :freezeMax),
                updated_at = NOW()
            FROM badge_grant
            WHERE user_profiles.user_id = badge_grant.user_id
            """;

    private static final String FIND_ACTIVE_CUSTOM_BADGES = """
            SELECT id, grant_config::TEXT AS grant_config
            FROM badges
            WHERE is_active = TRUE AND grant_type = 'CUSTOM' AND grant_config IS NOT NULL
            """;

    private static final String FIND_ACTIVE_TEMPLATE_BADGES = """
            SELECT id, grant_config::TEXT AS grant_config
            FROM badges
            WHERE is_active = TRUE AND grant_type = 'TEMPLATE' AND grant_config IS NOT NULL
            """;

    private static final String HAS_COMPLETED_REGION =
            SqlLoader.load(BadgeEngineJdbcRepository.class, "hasCompletedRegion");

    private static final String HAS_COMPLETED_ALL_REGIONS =
            SqlLoader.load(BadgeEngineJdbcRepository.class, "hasCompletedAllRegions");

    private static final String HAS_COMPLETED_SUBREGION =
            SqlLoader.load(BadgeEngineJdbcRepository.class, "hasCompletedSubregion");

    private static final String HAS_MASTERED_ALL_BUDGET_CATEGORIES =
            SqlLoader.load(BadgeEngineJdbcRepository.class, "hasMasteredAllBudgetCategories");

    private static final String INSERT_EVAL_LOG = """
            INSERT INTO badge_evaluation_log (id, badge_id, evaluation_type, users_evaluated, users_granted, started_at, completed_at, duration_ms, error_message)
            VALUES (:id, :badgeId, :evaluationType, :usersEvaluated, :usersGranted, :startedAt, :completedAt, :durationMs, :errorMessage)
            """;

    public List<TemplateBadgeRow> findUnclaimedTemplateBadges(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return jdbc.query(FIND_UNCLAIMED_TEMPLATES, params, (rs, rowNum) ->
                new TemplateBadgeRow(rs.getLong("id"), rs.getString("grant_config")));
    }

    public UserStatsRow loadUserStats(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return jdbc.query(LOAD_USER_STATS, params, (rs, rowNum) -> new UserStatsRow(
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
                rs.getLong("budget_categories_mastered"),
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
                rs.getLong("quiz_total")
        )).stream().findFirst().orElse(null);
    }

    public void grantBadge(Long userId, Long badgeId, int freezePerBadge, int freezeMax) {
        var params = new MapSqlParameterSource()
                .addValue("id", TSID.Factory.getTsid().toLong())
                .addValue("userId", userId)
                .addValue("badgeId", badgeId)
                .addValue("freezePerBadge", freezePerBadge)
                .addValue("freezeMax", freezeMax);
        jdbc.update(GRANT_BADGE_WITH_SHIELD, params);
    }

    public List<TemplateBadgeRow> findActiveCustomBadges() {
        return jdbc.query(FIND_ACTIVE_CUSTOM_BADGES, (rs, rowNum) ->
                new TemplateBadgeRow(rs.getLong("id"), rs.getString("grant_config")));
    }

    public List<TemplateBadgeRow> findActiveTemplateBadges() {
        return jdbc.query(FIND_ACTIVE_TEMPLATE_BADGES, (rs, rowNum) ->
                new TemplateBadgeRow(rs.getLong("id"), rs.getString("grant_config")));
    }

    public boolean hasCompletedRegion(Long userId, String region) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("region", region);
        return Boolean.TRUE.equals(jdbc.queryForObject(HAS_COMPLETED_REGION, params, Boolean.class));
    }

    public boolean hasCompletedSubregion(Long userId, String subregion) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("subregion", subregion);
        return Boolean.TRUE.equals(jdbc.queryForObject(HAS_COMPLETED_SUBREGION, params, Boolean.class));
    }

    public boolean hasCompletedAllRegions(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return Boolean.TRUE.equals(jdbc.queryForObject(HAS_COMPLETED_ALL_REGIONS, params, Boolean.class));
    }

    public boolean hasMasteredAllBudgetCategories(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return Boolean.TRUE.equals(jdbc.queryForObject(
                HAS_MASTERED_ALL_BUDGET_CATEGORIES, params, Boolean.class));
    }

    public int batchGrant(List<Long> userIds, Long badgeId, int freezePerBadge, int freezeMax) {
        if (userIds.isEmpty()) return 0;
        SqlParameterSource[] batch = userIds.stream()
                .map(uid -> (SqlParameterSource) new MapSqlParameterSource()
                        .addValue("id", TSID.Factory.getTsid().toLong())
                        .addValue("userId", uid)
                        .addValue("badgeId", badgeId)
                        .addValue("freezePerBadge", freezePerBadge)
                        .addValue("freezeMax", freezeMax))
                .toArray(SqlParameterSource[]::new);
        return Arrays.stream(jdbc.batchUpdate(GRANT_BADGE_WITH_SHIELD, batch)).sum();
    }

    public void insertEvalLog(Long id, Long badgeId, String evaluationType,
                              int usersEvaluated, int usersGranted,
                              Instant startedAt, Instant completedAt,
                              Long durationMs, String errorMessage) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("badgeId", badgeId)
                .addValue("evaluationType", evaluationType)
                .addValue("usersEvaluated", usersEvaluated)
                .addValue("usersGranted", usersGranted)
                .addValue("startedAt", java.sql.Timestamp.from(startedAt))
                .addValue("completedAt", completedAt != null ? java.sql.Timestamp.from(completedAt) : null)
                .addValue("durationMs", durationMs)
                .addValue("errorMessage", errorMessage);
        jdbc.update(INSERT_EVAL_LOG, params);
    }
}
