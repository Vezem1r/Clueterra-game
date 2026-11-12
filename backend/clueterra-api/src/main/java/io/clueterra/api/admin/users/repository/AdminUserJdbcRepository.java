package io.clueterra.api.admin.users.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.users.dto.AdminUserDto;
import io.clueterra.api.rivergame.dto.RiverPlayerStatsDto;
import io.clueterra.api.flaggame.dto.FlagPlayerStatsDto;
import io.clueterra.api.quizgame.dto.QuizPlayerStatsDto;
import io.clueterra.api.silhouettegame.dto.SilhouettePlayerStatsDto;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.database.SortDirection;
import io.clueterra.api.common.enums.Role;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminUserJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String BASE_SELECT =
            SqlLoader.load(AdminUserJdbcRepository.class, "baseSelect");

    private static final String BASE_COUNT = """
            SELECT COUNT(*) FROM users u
            LEFT JOIN user_profiles p ON u.id = p.user_id
            """;

    private static final Set<String> ALLOWED_SORT = Set.of(
            "email", "role", "enabled", "createdAt", "displayName",
            "totalXp", "currentLevel", "challengesPlayed", "challengesWon",
            "currentStreak", "bestStreak", "budgetCompletedChallenges", "riversPlayed", "silhouettesPlayed", "flagsPlayed", "quizCountries", "reportsMuted");

    private static final Map<String, String> SORT_MAP = Map.ofEntries(
            Map.entry("email", "u.email"),
            Map.entry("role", "u.role"),
            Map.entry("enabled", "u.enabled"),
            Map.entry("createdAt", "u.created_at"),
            Map.entry("displayName", "p.display_name"),
            Map.entry("totalXp", "p.total_xp"),
            Map.entry("currentLevel", "p.current_level"),
            Map.entry("challengesPlayed", "p.challenges_played"),
            Map.entry("challengesWon", "p.challenges_won"),
            Map.entry("currentStreak", "p.current_streak"),
            Map.entry("bestStreak", "p.best_streak"),
            Map.entry("budgetCompletedChallenges", "bs.completed_challenges"),
            Map.entry("riversPlayed", "COALESCE(rs.attempted_rivers, 0)"),
            Map.entry("silhouettesPlayed", "COALESCE(ss.attempted_countries, 0)"),
            Map.entry("flagsPlayed", "COALESCE(fs.attempted_flags, 0)"),
            Map.entry("quizCountries", "COALESCE(qs.explored_countries, 0)"),
            Map.entry("reportsMuted", "u.reports_muted"));

    private static final RowMapper<AdminUserDto> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    private static AdminUserDto mapRow(ResultSet rs) throws SQLException {
        return new AdminUserDto(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("username"),
                rs.getString("role"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime().toString() : null,
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                rs.getInt("total_xp"),
                rs.getInt("current_level"),
                rs.getInt("challenges_played"),
                rs.getInt("challenges_won"),
                rs.getInt("current_streak"),
                rs.getInt("best_streak"),
                rs.getInt("streak_freezes"),
                rs.getInt("budget_completed_challenges"),
                rs.getInt("budget_answered_questions"),
                rs.getDouble("budget_full_xp_rate"),
                rs.getDouble("budget_average_accuracy"),
                rs.getInt("budget_clean_sweeps"),
                rs.getInt("budget_projects_explored"),
                rs.getInt("budget_best_higher_lower_streak"),
                rs.getBigDecimal("budget_projects_mastered_value_usd"),
                new RiverPlayerStatsDto(
                        rs.getLong("river_attempted"),
                        rs.getLong("river_solved"),
                        rs.getLong("river_guesses"),
                        rs.getLong("river_skips"),
                        rs.getLong("river_xp_awarded"),
                        rs.getObject("river_current_question_id", Long.class)),
                new SilhouettePlayerStatsDto(
                        rs.getLong("silhouette_attempted"),
                        rs.getLong("silhouette_solved"),
                        rs.getLong("silhouette_total"),
                        rs.getLong("silhouette_guesses"),
                        rs.getLong("silhouette_skips"),
                        rs.getLong("silhouette_xp_awarded")),
                new FlagPlayerStatsDto(
                        rs.getLong("flag_attempted"),
                        rs.getLong("flag_solved"),
                        rs.getLong("flag_total"),
                        rs.getLong("flag_guesses"),
                        rs.getLong("flag_skips"),
                        rs.getLong("flag_xp_awarded")),
                new QuizPlayerStatsDto(
                        rs.getLong("quiz_explored"),
                        rs.getLong("quiz_mastered"),
                        rs.getLong("quiz_total"),
                        rs.getLong("quiz_completed_levels"),
                        rs.getLong("quiz_total_levels"),
                        rs.getLong("quiz_answered"),
                        rs.getLong("quiz_correct"),
                        rs.getLong("quiz_xp_awarded")),
                rs.getBoolean("reports_muted"),
                rs.getBoolean("profile_locked"),
                rs.getBoolean("is_guest"));
    }

    public List<AdminUserDto> findAdmins(String search, Boolean enabled,
                                          Integer minRiversPlayed, Integer maxRiversPlayed) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .eq("u.role", "adminRole", Role.ADMIN.name())
                .ilike("LOWER(u.email) LIKE :search OR LOWER(p.display_name) LIKE :search OR LOWER(u.username) LIKE :search", "search", search)
                .bool("u.enabled", "enabled", enabled)
                .gteInt("COALESCE(rs.attempted_rivers, 0)", "minRiversPlayed", minRiversPlayed)
                .lteInt("COALESCE(rs.attempted_rivers, 0)", "maxRiversPlayed", maxRiversPlayed);
        return jdbc.query(BASE_SELECT + f.whereClause() + " ORDER BY u.created_at", f.params(), ROW_MAPPER);
    }

    public Page<AdminUserDto> findAll(String search, String role, Boolean enabled,
                                      Boolean reportsMuted, Boolean isGuest,
                                      Integer minRiversPlayed, Integer maxRiversPlayed,
                                      String sortBy, String sortDir,
                                      Pageable pageable, boolean excludeAdmins) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .neq("u.role", "adminRole", excludeAdmins ? Role.ADMIN.name() : null)
                .ilike("LOWER(u.email) LIKE :search OR LOWER(p.display_name) LIKE :search OR LOWER(u.username) LIKE :search", "search", search)
                .eq("u.role", "role", role)
                .bool("u.enabled", "enabled", enabled)
                .bool("u.reports_muted", "reportsMuted", reportsMuted)
                .bool("u.is_guest", "isGuest", isGuest)
                .gteInt("COALESCE(rs.attempted_rivers, 0)", "minRiversPlayed", minRiversPlayed)
                .lteInt("COALESCE(rs.attempted_rivers, 0)", "maxRiversPlayed", maxRiversPlayed);

        String countJoins = minRiversPlayed != null || maxRiversPlayed != null
                ? " LEFT JOIN user_river_stats rs ON u.id = rs.user_id" : "";
        Long total = jdbc.queryForObject(BASE_COUNT + countJoins + f.whereClause(), f.params(), Long.class);
        if (total == null || total == 0) return Page.empty(pageable);

        String col = ALLOWED_SORT.contains(sortBy) ? SORT_MAP.getOrDefault(sortBy, "u.created_at") : "u.created_at";
        String dir = SortDirection.resolve(sortDir);

        String sql = BASE_SELECT + f.whereClause()
                + " ORDER BY " + col + " " + dir
                + " LIMIT :limit OFFSET :offset";
        f.params().addValue("limit", pageable.getPageSize());
        f.params().addValue("offset", pageable.getOffset());

        return new PageImpl<>(jdbc.query(sql, f.params(), ROW_MAPPER), pageable, total);
    }

    public Optional<AdminUserDto> findById(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        List<AdminUserDto> list = jdbc.query(BASE_SELECT + " WHERE u.id = :id", params, ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public int update(Long id, String role, Boolean enabled, Boolean reportsMuted, Boolean profileLocked) {
        List<String> sets = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);

        if (role != null) {
            sets.add("role = :role");
            params.addValue("role", role);
        }
        if (enabled != null) {
            sets.add("enabled = :enabled");
            params.addValue("enabled", enabled);
        }
        if (reportsMuted != null) {
            sets.add("reports_muted = :reportsMuted");
            params.addValue("reportsMuted", reportsMuted);
        }
        if (profileLocked != null) {
            sets.add("profile_locked = :profileLocked");
            params.addValue("profileLocked", profileLocked);
        }

        if (sets.isEmpty()) return 0;

        String sql = "UPDATE users SET " + String.join(", ", sets) + " WHERE id = :id";
        return jdbc.update(sql, params);
    }

    private static final String EXISTS_BY_USERNAME = """
            SELECT EXISTS(SELECT 1 FROM users WHERE LOWER(username) = LOWER(:username) AND id != :excludeUserId)
            """;

    public boolean existsByUsername(Long excludeUserId, String username) {
        var params = new MapSqlParameterSource()
                .addValue("excludeUserId", excludeUserId)
                .addValue("username", username);
        return Boolean.TRUE.equals(jdbc.queryForObject(EXISTS_BY_USERNAME, params, Boolean.class));
    }

    public void updateUsername(Long userId, String username) {
        jdbc.update("UPDATE users SET username = :username, updated_at = NOW() WHERE id = :userId",
                new MapSqlParameterSource("userId", userId).addValue("username", username));
    }

    public void updateProfile(Long userId, String displayName,
                              Integer totalXp, Integer challengesPlayed, Integer challengesWon,
                              Integer currentStreak, Integer bestStreak, Integer streakFreezes,
                              int levelDivisor) {
        List<String> sets = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);

        if (displayName != null) {
            sets.add("display_name = :displayName");
            params.addValue("displayName", displayName);
        }
        if (totalXp != null) {
            sets.add("total_xp = :totalXp");
            sets.add("current_level = GREATEST(current_level, CAST(FLOOR(SQRT(CAST(:totalXp AS double precision) / CAST(:levelDivisor AS double precision))) + 1 AS integer))");
            params.addValue("totalXp", totalXp);
            params.addValue("levelDivisor", levelDivisor);
        }
        if (challengesPlayed != null) {
            sets.add("challenges_played = :challengesPlayed");
            params.addValue("challengesPlayed", challengesPlayed);
        }
        if (challengesWon != null) {
            sets.add("challenges_won = :challengesWon");
            params.addValue("challengesWon", challengesWon);
        }
        if (currentStreak != null) {
            sets.add("current_streak = :currentStreak");
            params.addValue("currentStreak", currentStreak);
        }
        if (bestStreak != null) {
            sets.add("best_streak = :bestStreak");
            params.addValue("bestStreak", bestStreak);
        }
        if (streakFreezes != null) {
            sets.add("streak_freezes = :streakFreezes");
            params.addValue("streakFreezes", streakFreezes);
        }

        if (sets.isEmpty()) return;

        String sql = "UPDATE user_profiles SET " + String.join(", ", sets) + " WHERE user_id = :userId";
        jdbc.update(sql, params);
    }

    public void updateAvatarUrl(Long userId, String avatarUrl) {
        jdbc.update("UPDATE user_profiles SET avatar_url = :avatarUrl, updated_at = NOW() WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId).addValue("avatarUrl", avatarUrl));
    }

    public List<String> findDistinctRoles() {
        return jdbc.queryForList("SELECT DISTINCT role FROM users ORDER BY role", Map.of(), String.class);
    }
}
