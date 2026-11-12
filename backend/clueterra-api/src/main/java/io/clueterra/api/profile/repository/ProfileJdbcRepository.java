package io.clueterra.api.profile.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;
import io.clueterra.api.profile.dto.ProfileBadgeDto;
import io.clueterra.api.profile.dto.ProfileDifficultyStatsDto;
import io.clueterra.api.profile.dto.UnacknowledgedBadgeDto;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProfileJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String FIND_OWN_PROFILE = SqlLoader.load(ProfileJdbcRepository.class, "findOwnProfile");
    private static final String FIND_BY_USERNAME = SqlLoader.load(ProfileJdbcRepository.class, "findByUsername");
    private static final String UPSERT_PROFILE = SqlLoader.load(ProfileJdbcRepository.class, "upsertProfile");
    private static final String COUNT_HIDDEN_SECRETS = SqlLoader.load(ProfileJdbcRepository.class, "countHiddenSecrets");
    private static final String FIND_UNACKNOWLEDGED = SqlLoader.load(ProfileJdbcRepository.class, "findUnacknowledged");
    private static final String FIND_BADGES_PAGINATED = SqlLoader.load(ProfileJdbcRepository.class, "findBadgesPaginated");
    private static final String FIND_PUBLIC_BADGES_PAGINATED = SqlLoader.load(ProfileJdbcRepository.class, "findPublicBadgesPaginated");
    private static final String COUNT_BADGES_FILTERED = SqlLoader.load(ProfileJdbcRepository.class, "countBadgesFiltered");
    private static final String COUNT_PUBLIC_BADGES = SqlLoader.load(ProfileJdbcRepository.class, "countPublicBadges");
    private static final String FIND_RANDOM_BADGE_SAMPLES = SqlLoader.load(ProfileJdbcRepository.class, "findRandomBadgeSamples");
    private static final String FIND_DIFFICULTY_STATS = SqlLoader.load(ProfileJdbcRepository.class, "findDifficultyStats");

    private static final String ACKNOWLEDGE_BADGES = """
            UPDATE user_badges SET is_acknowledged = TRUE WHERE user_id = :userId AND badge_id IN (:badgeIds)
            """;

    private static final String ACKNOWLEDGE_STREAK_FREEZE_NOTICE = """
            UPDATE user_profiles
            SET streak_freezes_acknowledged_since_completion = streak_freezes_used_since_completion,
                updated_at = NOW()
            WHERE user_id = :userId
              AND streak_freezes_acknowledged_since_completion < streak_freezes_used_since_completion
            """;

    private static final RowMapper<ProfileRow> OWN_PROFILE_MAPPER = (rs, rowNum) -> {
        Number leaderboardRank = (Number) rs.getObject("leaderboard_rank");
        return new ProfileRow(
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                rs.getLong("total_xp"),
                rs.getInt("current_level"),
                rs.getInt("completed_runs"),
                rs.getInt("countries_solved"),
                rs.getInt("perfect_runs"),
                rs.getDouble("solve_rate"),
                rs.getInt("current_streak"),
                rs.getInt("best_streak"),
                leaderboardRank != null ? leaderboardRank.intValue() : null,
                rs.getString("favorite_difficulty"),
                rs.getBoolean("profile_locked"),
                rs.getInt("streak_freezes"),
                rs.getInt("streak_freezes_used_since_completion"),
                rs.getInt("streak_freezes_acknowledged_since_completion"),
                rs.getBoolean("today_completed"),
                rs.getBoolean("freeze_used_today"),
                rs.getBoolean("social_discoverable"),
                rs.getInt("budget_completed_challenges"),
                rs.getInt("budget_projects_explored"),
                rs.getBigDecimal("budget_projects_mastered_value_usd"));
    };

    private static final RowMapper<ProfileRow> PUBLIC_PROFILE_MAPPER = (rs, rowNum) -> {
        Number leaderboardRank = (Number) rs.getObject("leaderboard_rank");
        return ProfileRow.publicProfile(
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                rs.getLong("total_xp"),
                rs.getInt("current_level"),
                rs.getInt("completed_runs"),
                rs.getInt("countries_solved"),
                rs.getInt("perfect_runs"),
                rs.getDouble("solve_rate"),
                rs.getInt("current_streak"),
                rs.getInt("best_streak"),
                leaderboardRank != null ? leaderboardRank.intValue() : null,
                rs.getString("favorite_difficulty"),
                rs.getInt("streak_freezes"),
                rs.getInt("budget_completed_challenges"),
                rs.getInt("budget_projects_explored"),
                rs.getBigDecimal("budget_projects_mastered_value_usd"));
    };

    public Optional<ProfileRow> findOwnProfile(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return jdbc.query(FIND_OWN_PROFILE, params, OWN_PROFILE_MAPPER).stream().findFirst();
    }

    public Optional<ProfileRow> findByUsername(String username) {
        var params = new MapSqlParameterSource("username", username);
        return jdbc.query(FIND_BY_USERNAME, params, PUBLIC_PROFILE_MAPPER).stream().findFirst();
    }

    public Optional<ProfileRow> upsertProfile(
            Long userId,
            String displayName,
            String avatarUrl,
            Boolean socialDiscoverable) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("displayName", displayName)
                .addValue("avatarUrl", avatarUrl)
                .addValue("socialDiscoverable", socialDiscoverable);
        return jdbc.query(UPSERT_PROFILE, params, OWN_PROFILE_MAPPER).stream().findFirst();
    }

    public List<ProfileDifficultyStatsDto> findDifficultyStats(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return jdbc.query(FIND_DIFFICULTY_STATS, params, (rs, rowNum) -> {
            var lastCompletedAt = rs.getTimestamp("last_completed_at");
            return new ProfileDifficultyStatsDto(
                    rs.getString("level"),
                    rs.getInt("completed_runs"),
                    rs.getInt("countries_played"),
                    rs.getInt("countries_solved"),
                    rs.getDouble("solve_rate"),
                    lastCompletedAt != null ? lastCompletedAt.toInstant() : null);
        });
    }

    private static final String UPDATE_AVATAR = """
            UPDATE user_profiles SET avatar_url = :avatarUrl, updated_at = NOW()
            WHERE user_id = :userId
            """;

    public void updateAvatarUrl(Long userId, String avatarUrl) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("avatarUrl", avatarUrl);
        jdbc.update(UPDATE_AVATAR, params);
    }

    private static final RowMapper<ProfileBadgeDto> BADGE_ROW_MAPPER = (rs, rowNum) -> new ProfileBadgeDto(
            rs.getLong("id"),
            rs.getString("slug"),
            rs.getString("image_url"),
            rs.getString("accent_color_key"),
            rs.getString("effect_color_key"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBoolean("unlocked"),
            rs.getString("granted_at"),
            null
    );

    public List<ProfileBadgeDto> findBadgesPaginated(Long userId, String locale, String filter, int page, int size) {
        long offset = Math.multiplyExact((long) page, (long) size);
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("locale", locale)
                .addValue("filter", filter)
                .addValue("pageSize", size)
                .addValue("offset", offset);
        return jdbc.query(FIND_BADGES_PAGINATED, params, BADGE_ROW_MAPPER);
    }

    public List<ProfileBadgeDto> findPublicBadgesPaginated(String username, String locale, int page, int size) {
        long offset = Math.multiplyExact((long) page, (long) size);
        var params = new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("locale", locale)
                .addValue("pageSize", size)
                .addValue("offset", offset);
        return jdbc.query(FIND_PUBLIC_BADGES_PAGINATED, params, BADGE_ROW_MAPPER);
    }

    public int countBadgesFiltered(Long userId, String filter) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("filter", filter);
        Integer count = jdbc.queryForObject(COUNT_BADGES_FILTERED, params, Integer.class);
        return count != null ? count : 0;
    }

    public int countPublicBadges(String username) {
        var params = new MapSqlParameterSource("username", username);
        Integer count = jdbc.queryForObject(COUNT_PUBLIC_BADGES, params, Integer.class);
        return count != null ? count : 0;
    }

    public int countHiddenSecrets(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        Integer count = jdbc.queryForObject(COUNT_HIDDEN_SECRETS, params, Integer.class);
        return count != null ? count : 0;
    }

    public List<UnacknowledgedBadgeDto> findUnacknowledged(Long userId, String locale) {
        var params = new MapSqlParameterSource("userId", userId).addValue("locale", locale);
        return jdbc.query(FIND_UNACKNOWLEDGED, params, (rs, rowNum) -> new UnacknowledgedBadgeDto(
                rs.getLong("id"),
                rs.getLong("badge_id"),
                rs.getString("slug"),
                rs.getString("image_url"),
                rs.getString("accent_color_key"),
                rs.getString("effect_color_key"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("granted_at")
        ));
    }

    public List<ProfileBadgeDto> findRandomBadgeSamples(String locale, int limit) {
        var params = new MapSqlParameterSource()
                .addValue("locale", locale)
                .addValue("limit", limit);
        return jdbc.query(FIND_RANDOM_BADGE_SAMPLES, params, BADGE_ROW_MAPPER);
    }

    public void acknowledgeBadges(Long userId, List<Long> badgeIds) {
        var params = new MapSqlParameterSource("userId", userId).addValue("badgeIds", badgeIds);
        jdbc.update(ACKNOWLEDGE_BADGES, params);
    }

    public void acknowledgeStreakFreezeNotice(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        jdbc.update(ACKNOWLEDGE_STREAK_FREEZE_NOTICE, params);
    }
}
