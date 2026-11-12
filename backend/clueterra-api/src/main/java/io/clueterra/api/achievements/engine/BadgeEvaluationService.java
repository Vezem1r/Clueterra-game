package io.clueterra.api.achievements.engine;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.common.enums.BadgeTrigger;
import io.hypersistence.tsid.TSID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeEvaluationService {

    private final BadgeEngineJdbcRepository engineRepo;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "challenges_played", "challenges_won", "current_streak", "best_streak",
            "total_xp", "current_level", "win_rate", "account_age_days"
    );

    private static final Set<String> ALLOWED_OPERATORS = Set.of(">=", "<=", "=", ">", "<", "!=");

    public void evaluateAllCustomBadges(String evaluationType) {
        List<TemplateBadgeRow> badges = engineRepo.findActiveCustomBadges();
        log.info("Evaluating {} custom badges", badges.size());
        for (TemplateBadgeRow badge : badges) {
            evaluateSingleBadge(badge.id(), evaluationType);
        }
        evaluateTemplateBadgesInBatch(evaluationType);
    }

    public void evaluateTemplateBadgesInBatch(String evaluationType) {
        List<TemplateBadgeRow> badges = engineRepo.findActiveTemplateBadges();
        log.info("Evaluating {} template badges in batch", badges.size());
        for (TemplateBadgeRow badge : badges) {
            evaluateSingleTemplateBadge(badge, evaluationType);
        }
    }

    private void evaluateSingleTemplateBadge(TemplateBadgeRow badge, String evaluationType) {
        Instant startedAt = Instant.now();
        try {
            JsonNode config = objectMapper.readTree(badge.grantConfig());
            BadgeTrigger trigger = BadgeTrigger.valueOf(config.path("trigger").asText());

            MapSqlParameterSource params = new MapSqlParameterSource("badgeId", badge.id());
            String condition = buildTriggerCondition(trigger, config, params);
            String statsJoin = switch (trigger) {
                case RIVER_UNIQUE_ATTEMPTED, RIVER_UNIQUE_SOLVED ->
                        "LEFT JOIN cl_api_schema.user_river_stats rs ON rs.user_id = u.id\n";
                case SILHOUETTE_UNIQUE_SOLVED, SILHOUETTE_GLOBE_COMPLETED ->
                        "LEFT JOIN cl_api_schema.user_silhouette_stats ss ON ss.user_id = u.id\n";
                case FLAG_UNIQUE_SOLVED, FLAG_PLAZA_COMPLETED ->
                        "LEFT JOIN cl_api_schema.user_flag_stats fs ON fs.user_id = u.id\n";
                case QUIZ_UNIQUE_EXPLORED, QUIZ_TOUR_COMPLETED ->
                        "LEFT JOIN cl_api_schema.user_quiz_stats qs ON qs.user_id = u.id\n";
                default -> "";
            };

            String sql = """
                    SELECT u.id FROM users u
                    JOIN user_profiles p ON u.id = p.user_id
                    LEFT JOIN user_budget_stats bs ON u.id = bs.user_id
                    """ + statsJoin + """
                    LEFT JOIN user_badges ub ON u.id = ub.user_id AND ub.badge_id = :badgeId
                    WHERE ub.id IS NULL AND u.enabled = TRUE
                      AND (u.is_guest = FALSE OR u.guest_token_expires_at > NOW())
                      AND (""" + condition + ")";

            List<Long> userIds = jdbc.queryForList(sql, params, Long.class);
            if (userIds.isEmpty()) return;
            int freezePerBadge = settingsService.getStreakFreezePerBadge();
            int freezeMax = settingsService.getStreakFreezeMax();
            int granted = engineRepo.batchGrant(userIds, badge.id(), freezePerBadge, freezeMax);

            Instant completedAt = Instant.now();
            long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();

            engineRepo.insertEvalLog(
                    TSID.Factory.getTsid().toLong(), badge.id(), evaluationType,
                    userIds.size(), granted, startedAt, completedAt, durationMs, null
            );

            log.info("Template badge {} evaluated: {} checked, {} granted in {}ms",
                    badge.id(), userIds.size(), granted, durationMs);

        } catch (Exception e) {
            Instant completedAt = Instant.now();
            long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
            engineRepo.insertEvalLog(
                    TSID.Factory.getTsid().toLong(), badge.id(), evaluationType,
                    0, 0, startedAt, completedAt, durationMs, e.getMessage()
            );
            log.error("Failed to evaluate template badge {}", badge.id(), e);
        }
    }

    private String buildTriggerCondition(BadgeTrigger trigger, JsonNode config, MapSqlParameterSource params) {
        long threshold = config.path("threshold").asLong(1);
        return switch (trigger) {
            case LEVEL_REACHED -> {
                params.addValue("thr", threshold);
                yield "p.current_level >= :thr";
            }
            case STREAK_COUNT -> {
                params.addValue("thr", threshold);
                yield "p.current_streak >= :thr";
            }
            case BEST_STREAK -> {
                params.addValue("thr", threshold);
                yield "p.best_streak >= :thr";
            }
            case GAMES_PLAYED -> {
                params.addValue("thr", threshold);
                yield "p.challenges_played >= :thr";
            }
            case GAMES_WON -> {
                params.addValue("thr", threshold);
                yield "p.challenges_won >= :thr";
            }
            case TOTAL_XP -> {
                params.addValue("thr", threshold);
                yield "p.total_xp >= :thr";
            }
            case FIRST_WIN -> "p.challenges_won >= 1";
            case REGION_COMPLETED -> {
                String region = config.path("threshold").asText();
                if ("ALL".equals(region)) {
                    yield """
                            NOT EXISTS (
                                SELECT 1 FROM cl_schema.countries c
                                WHERE c.is_active = TRUE
                                AND NOT EXISTS (
                                    SELECT 1 FROM user_country_solutions solved
                                    WHERE solved.user_id = u.id AND solved.country_id = c.id
                                )
                            )""";
                }
                if (region.startsWith("sub:")) {
                    params.addValue("subregion", region.substring(4));
                    yield """
                            NOT EXISTS (
                                SELECT 1 FROM cl_schema.countries c
                                WHERE c.subregion = :subregion AND c.is_active = TRUE
                                AND NOT EXISTS (
                                    SELECT 1 FROM user_country_solutions solved
                                    WHERE solved.user_id = u.id AND solved.country_id = c.id
                                )
                            )""";
                }
                params.addValue("region", region);
                yield """
                        NOT EXISTS (
                            SELECT 1 FROM cl_schema.countries c
                            WHERE c.region = :region AND c.is_active = TRUE
                            AND NOT EXISTS (
                                SELECT 1 FROM user_country_solutions solved
                                WHERE solved.user_id = u.id AND solved.country_id = c.id
                            )
                        )""";
            }
            case PERFECT_GAME -> "p.perfect_runs >= 1";
            case BUDGET_CHALLENGES_COMPLETED -> budgetThreshold(params, threshold, "completed_challenges");
            case BUDGET_EXACT_GUESSES -> budgetThreshold(params, threshold, "exact_guesses");
            case BUDGET_CLEAN_SWEEPS -> budgetThreshold(params, threshold, "clean_sweeps");
            case BUDGET_UNIQUE_PROJECTS -> budgetThreshold(params, threshold, "unique_projects_explored");
            case BUDGET_BEST_HIGHER_LOWER_STREAK -> budgetThreshold(params, threshold, "higher_lower_best_streak");
            case BUDGET_HIGHER_LOWER_CORRECT -> budgetThreshold(params, threshold, "higher_lower_correct");
            case BUDGET_COST_PER_UNIT_CORRECT -> budgetThreshold(params, threshold, "cost_per_unit_correct");
            case BUDGET_DISTINCT_COUNTRIES -> budgetThreshold(params, threshold, "countries_mastered");
            case BUDGET_MASTERED_VALUE_USD -> budgetThreshold(params, threshold, "mastered_project_value_usd");
            case BUDGET_SEVERE_MISSES -> budgetThreshold(params, threshold, "severe_misses");
            case RIVER_UNIQUE_ATTEMPTED -> {
                params.addValue("thr", threshold);
                yield "COALESCE(rs.attempted_rivers, 0) >= :thr";
            }
            case RIVER_UNIQUE_SOLVED -> {
                params.addValue("thr", threshold);
                yield "COALESCE(rs.solved_rivers, 0) >= :thr";
            }
            case SILHOUETTE_UNIQUE_SOLVED -> {
                params.addValue("thr", threshold);
                yield "COALESCE(ss.solved_countries, 0) >= :thr";
            }
            case SILHOUETTE_GLOBE_COMPLETED -> """
                    EXISTS (SELECT 1 FROM cl_schema.silhouette_game_question WHERE is_active)
                    AND COALESCE(ss.solved_countries, 0) >= (SELECT COUNT(*) FROM cl_schema.silhouette_game_question WHERE is_active)
                    """;
            case FLAG_UNIQUE_SOLVED -> {
                params.addValue("thr", threshold);
                yield "COALESCE(fs.solved_flags, 0) >= :thr";
            }
            case FLAG_PLAZA_COMPLETED -> """
                    EXISTS (SELECT 1 FROM cl_schema.flag_game_question WHERE is_active)
                    AND COALESCE(fs.solved_flags, 0) >= (SELECT COUNT(*) FROM cl_schema.flag_game_question WHERE is_active)
                    """;
            case QUIZ_UNIQUE_EXPLORED -> {
                params.addValue("thr", threshold);
                yield "COALESCE(qs.explored_countries, 0) >= :thr";
            }
            case QUIZ_TOUR_COMPLETED -> """
                    EXISTS (SELECT 1 FROM cl_schema.quiz_game_country WHERE is_active)
                    AND COALESCE(qs.explored_countries, 0) >= (SELECT COUNT(*) FROM cl_schema.quiz_game_country WHERE is_active)
                    """;
            case BUDGET_ALL_CATEGORIES -> """
                    EXISTS (
                        SELECT 1 FROM cl_budget_schema.question q
                        WHERE q.is_published = TRUE
                    )
                    AND NOT EXISTS (
                        SELECT DISTINCT q.category
                        FROM cl_budget_schema.question q
                        WHERE q.is_published = TRUE
                        EXCEPT
                        SELECT progress.category
                        FROM user_budget_project_progress progress
                        WHERE progress.user_id = u.id AND progress.first_mastered_at IS NOT NULL
                    )""";
        };
    }

    private String budgetThreshold(MapSqlParameterSource params, long threshold, String field) {
        params.addValue("thr", threshold);
        return "COALESCE(bs." + field + ", 0) >= :thr";
    }

    public void evaluateSingleBadge(Long badgeId, String evaluationType) {
        Instant startedAt = Instant.now();
        try {
            String configJson = findBadgeConfig(badgeId);
            if (configJson == null) {
                log.warn("No grant_config for badge {}", badgeId);
                return;
            }

            JsonNode config = objectMapper.readTree(configJson);
            JsonNode filters = config.path("filters");
            String logic = config.path("logic").asText("AND");

            if (!filters.isArray() || filters.isEmpty()) {
                log.warn("Badge {} has no filters in grant_config", badgeId);
                return;
            }

            StringBuilder whereClause = new StringBuilder();
            MapSqlParameterSource params = new MapSqlParameterSource("badgeId", badgeId);
            int filterIdx = 0;

            for (JsonNode filter : filters) {
                String field = filter.path("field").asText();
                String op = filter.path("op").asText();
                String value = filter.path("value").asText();

                if (!ALLOWED_FIELDS.contains(field) || !ALLOWED_OPERATORS.contains(op)) {
                    log.warn("Skipping invalid filter: field={}, op={}", field, op);
                    continue;
                }

                if (filterIdx > 0) {
                    whereClause.append("OR".equalsIgnoreCase(logic) ? " OR " : " AND ");
                }

                String paramName = "fv" + filterIdx;
                String sqlField = mapFieldToSql(field);
                whereClause.append(sqlField).append(" ").append(op).append(" :").append(paramName);
                params.addValue(paramName, Double.parseDouble(value));
                filterIdx++;
            }

            if (filterIdx == 0) return;

            String sql = """
                    SELECT u.id FROM users u
                    JOIN user_profiles p ON u.id = p.user_id
                    LEFT JOIN user_badges ub ON u.id = ub.user_id AND ub.badge_id = :badgeId
                    WHERE ub.id IS NULL AND u.enabled = TRUE
                      AND (u.is_guest = FALSE OR u.guest_token_expires_at > NOW())
                      AND (""" + whereClause + ")";

            List<Long> userIds = jdbc.queryForList(sql, params, Long.class);
            if (userIds.isEmpty()) return;
            int freezePerBadge = settingsService.getStreakFreezePerBadge();
            int freezeMax = settingsService.getStreakFreezeMax();
            int granted = engineRepo.batchGrant(userIds, badgeId, freezePerBadge, freezeMax);

            Instant completedAt = Instant.now();
            long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();

            engineRepo.insertEvalLog(
                    TSID.Factory.getTsid().toLong(), badgeId, evaluationType,
                    userIds.size(), granted, startedAt, completedAt, durationMs, null
            );

            log.info("Badge {} evaluated: {} users checked, {} granted in {}ms",
                    badgeId, userIds.size(), granted, durationMs);

        } catch (Exception e) {
            Instant completedAt = Instant.now();
            long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();

            engineRepo.insertEvalLog(
                    TSID.Factory.getTsid().toLong(), badgeId, evaluationType,
                    0, 0, startedAt, completedAt, durationMs, e.getMessage()
            );

            log.error("Failed to evaluate badge badgeId={}", badgeId, e);
        }
    }

    private String mapFieldToSql(String field) {
        return switch (field) {
            case "win_rate" -> "win_rate(p.challenges_played, p.challenges_won)";
            case "account_age_days" -> "EXTRACT(DAY FROM NOW() - u.created_at)";
            default -> "p." + field;
        };
    }

    private String findBadgeConfig(Long badgeId) {
        var params = new MapSqlParameterSource("badgeId", badgeId);
        List<String> configs = jdbc.queryForList(
                "SELECT grant_config::TEXT FROM badges WHERE id = :badgeId AND is_active = TRUE",
                params, String.class);
        return configs.isEmpty() ? null : configs.getFirst();
    }
}
