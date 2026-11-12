package io.clueterra.api.game.repository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.common.utils.loader.SqlLoader;
import io.clueterra.api.game.service.ChallengeHintParser;

import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class GameSessionJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    private final String findByIdSql = SqlLoader.load(GameSessionJdbcRepository.class, "findSessionById");
    private final String findTodaySessionSql = SqlLoader.load(GameSessionJdbcRepository.class, "findTodaySessionForUser");
    private final String findTodaySummaryForUserSql = SqlLoader.load(GameSessionJdbcRepository.class, "findTodaySummaryForUser");
    private final String findGuessAttemptsForSessionSql = SqlLoader.load(GameSessionJdbcRepository.class, "findGuessAttemptsForSession");
    private final String findTodayCommunityStatsSql = SqlLoader.load(GameSessionJdbcRepository.class, "findTodayCommunityStats");
    private final String findTodayHardestCountrySql = SqlLoader.load(GameSessionJdbcRepository.class, "findTodayHardestCountry");
    private final String findGuestProgressForUserSql = SqlLoader.load(GameSessionJdbcRepository.class, "findGuestProgressForUser");

    private static final String INSERT_SESSION = """
            INSERT INTO game_sessions (id, user_id, guest_token, challenge_id, challenge_level,
                                       status, current_country_idx, started_at, total_xp_earned,
                                       max_hints_per_country, max_attempts, xp_before, level_before,
                                       terms_version_accepted, privacy_version_accepted, legal_accepted_at)
            VALUES (:id, :userId, :guestToken, :challengeId, :challengeLevel,
                    :status, 0, NOW(), 0, :maxHints, :maxAttempts,
                    COALESCE((SELECT total_xp FROM user_profiles WHERE user_id = :userId), 0),
                    COALESCE((SELECT current_level FROM user_profiles WHERE user_id = :userId), 1),
                    :termsVersionAccepted, :privacyVersionAccepted, :legalAcceptedAt)
            """;

    private static final String INSERT_SESSION_COUNTRY = """
            INSERT INTO game_session_countries (id, session_id, country_id, country_order,
                                                total_hints, hints_revealed, attempts_used, is_guessed,
                                                time_spent_ms, xp_earned)
            VALUES (:id, :sessionId, :countryId, :countryOrder,
                    :totalHints, :initialHintsRevealed, 0, false, 0, 0)
            """;

    private static final String INSERT_GUESS_ATTEMPT = """
            INSERT INTO guess_attempts (id, session_country_id, guessed_country_id,
                                        is_correct, attempt_number, hints_at_guess, created_at)
            VALUES (:id, :sessionCountryId, :guessedCountryId,
                    :isCorrect, :attemptNumber, :hintsAtGuess, NOW())
            """;

    private static final String UPDATE_SESSION_COUNTRY = """
            UPDATE game_session_countries
            SET hints_revealed = :hintsRevealed,
                attempts_used = :attemptsUsed,
                is_guessed = :isGuessed,
                guessed_at = :guessedAt,
                time_spent_ms = :timeSpentMs,
                xp_earned = :xpEarned
            WHERE id = :id
            """;

    private static final String UPDATE_CURRENT_COUNTRY_IDX = """
            UPDATE game_sessions SET current_country_idx = :idx WHERE id = :sessionId
            """;

    private static final String UPDATE_SESSION_ACTIVITY = """
            UPDATE game_sessions
            SET last_activity_at = :lastActivityAt
            WHERE id = :sessionId
            """;

    private static final String ACTIVATE_SESSION_COUNTRY = """
            UPDATE game_session_countries
            SET hints_revealed = CASE WHEN total_hints > 0 THEN 1 ELSE 0 END
            WHERE session_id = :sessionId
              AND country_order = :countryOrder
              AND hints_revealed = 0
            """;

    private static final String COMPLETE_SESSION = """
            UPDATE game_sessions
            SET status = :status, completed_at = NOW(), total_xp_earned = :totalXp,
                streak_bonus_percent = :streakBonusPercent, streak_bonus_xp = :streakBonusXp
            WHERE id = :sessionId
            """;

    private static final String APPLY_COMPLETED_SESSION_PROJECTION = """
            SELECT apply_completed_session_projection(:sessionId)
            """;

    private static final String RECORD_USER_XP_EVENT = """
            SELECT record_user_xp_event(:eventId, :userId, :sessionId, :xpAwarded,
                                        :sourceType, :sourceId, :challengeLevel, NOW())
            """;

    private static final String REASSIGN_SESSION_XP_EVENT = """
            SELECT reassign_session_xp_event(:sessionId, :targetUserId, :xpAwarded)
            """;

    private static final String ABANDON_EXPIRED_SESSIONS = """
            UPDATE game_sessions gs
            SET status = :abandonedStatus
            FROM daily_challenges dc
            WHERE dc.id = gs.challenge_id
              AND dc.challenge_date < :today
              AND gs.status = :inProgressStatus
            """;

    private static final String INSERT_PARTICIPATION = """
            INSERT INTO user_daily_participation (id, user_id, challenge_date, challenge_level, session_id, created_at)
            VALUES (:id, :userId, :challengeDate, :challengeLevel, :sessionId, NOW())
            ON CONFLICT (user_id, challenge_date) DO NOTHING
            """;

    private static final String EXISTS_PARTICIPATION = """
            SELECT EXISTS(
                SELECT 1 FROM user_daily_participation
                WHERE user_id = :userId AND challenge_date = :challengeDate
            )
            """;

    private static final String GET_SETTING = """
            SELECT value FROM app_settings WHERE key = :key
            """;

    private static final int DEFAULT_HINTS_PER_COUNTRY = 5;

    public long insertSession(Long userId, String guestToken, Long challengeId, String challengeLevel,
                              int maxHints, int maxAttempts, String termsVersionAccepted,
                              String privacyVersionAccepted, Instant legalAcceptedAt) {
        long id = TSID.Factory.getTsid().toLong();
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("guestToken", guestToken)
                .addValue("challengeId", challengeId)
                .addValue("challengeLevel", challengeLevel)
                .addValue("status", SessionStatus.IN_PROGRESS.name())
                .addValue("maxHints", maxHints)
                .addValue("maxAttempts", maxAttempts)
                .addValue("termsVersionAccepted", termsVersionAccepted)
                .addValue("privacyVersionAccepted", privacyVersionAccepted)
                .addValue("legalAcceptedAt", legalAcceptedAt != null ? java.sql.Timestamp.from(legalAcceptedAt) : null);
        jdbc.update(INSERT_SESSION, params);
        return id;
    }

    public void insertSessionCountriesBatch(long sessionId, List<ChallengeHintParser.SessionCountrySeed> entries) {
        SqlParameterSource[] batchParams = entries.stream()
                .map(e -> (SqlParameterSource) new MapSqlParameterSource()
                        .addValue("id", TSID.Factory.getTsid().toLong())
                        .addValue("sessionId", sessionId)
                        .addValue("countryId", e.countryId())
                        .addValue("countryOrder", e.order())
                        .addValue("totalHints", e.totalHints())
                        .addValue("initialHintsRevealed", e.order() == 1 && e.totalHints() > 0 ? 1 : 0))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_SESSION_COUNTRY, batchParams);
    }

    public void insertGuessAttempt(long sessionCountryId, long guessedCountryId, boolean isCorrect, int attemptNumber, int hintsAtGuess) {
        long id = TSID.Factory.getTsid().toLong();
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("sessionCountryId", sessionCountryId)
                .addValue("guessedCountryId", guessedCountryId)
                .addValue("isCorrect", isCorrect)
                .addValue("attemptNumber", attemptNumber)
                .addValue("hintsAtGuess", hintsAtGuess);
        jdbc.update(INSERT_GUESS_ATTEMPT, params);
    }

    public void updateSessionCountry(long id, int hintsRevealed, int attemptsUsed, boolean isGuessed, Instant guessedAt, long timeSpentMs, int xpEarned) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("hintsRevealed", hintsRevealed)
                .addValue("attemptsUsed", attemptsUsed)
                .addValue("isGuessed", isGuessed)
                .addValue("guessedAt", guessedAt != null ? java.sql.Timestamp.from(guessedAt) : null)
                .addValue("timeSpentMs", timeSpentMs)
                .addValue("xpEarned", xpEarned);
        jdbc.update(UPDATE_SESSION_COUNTRY, params);
    }

    public void updateSessionActivity(long sessionId, Instant lastActivityAt) {
        var params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("lastActivityAt", java.sql.Timestamp.from(lastActivityAt));
        jdbc.update(UPDATE_SESSION_ACTIVITY, params);
    }

    public void updateCurrentCountryIdx(long sessionId, int idx) {
        var params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("idx", idx);
        jdbc.update(UPDATE_CURRENT_COUNTRY_IDX, params);
    }

    public void activateSessionCountry(long sessionId, int countryOrder) {
        var params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("countryOrder", countryOrder);
        jdbc.update(ACTIVATE_SESSION_COUNTRY, params);
    }

    public void completeSession(long sessionId, int totalXp, double streakBonusPercent, int streakBonusXp) {
        var params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("totalXp", totalXp)
                .addValue("status", SessionStatus.COMPLETED.name())
                .addValue("streakBonusPercent", streakBonusPercent)
                .addValue("streakBonusXp", streakBonusXp);
        jdbc.update(COMPLETE_SESSION, params);
    }

    public void applyCompletedSessionProjection(long sessionId) {
        var params = new MapSqlParameterSource("sessionId", sessionId);
        jdbc.queryForObject(APPLY_COMPLETED_SESSION_PROJECTION, params, Boolean.class);
    }

    public void recordUserXpEvent(
            Long userId,
            long sessionId,
            int xpAwarded,
            String sourceType,
            Long sourceId,
            String challengeLevel) {
        var params = new MapSqlParameterSource()
                .addValue("eventId", TSID.Factory.getTsid().toLong())
                .addValue("userId", userId)
                .addValue("sessionId", sessionId)
                .addValue("xpAwarded", xpAwarded)
                .addValue("sourceType", sourceType)
                .addValue("sourceId", sourceId)
                .addValue("challengeLevel", challengeLevel);
        jdbc.queryForObject(RECORD_USER_XP_EVENT, params, Boolean.class);
    }

    public boolean reassignSessionXpEvent(long sessionId, Long targetUserId, int xpAwarded) {
        var params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("targetUserId", targetUserId)
                .addValue("xpAwarded", xpAwarded);
        return Boolean.TRUE.equals(jdbc.queryForObject(REASSIGN_SESSION_XP_EVENT, params, Boolean.class));
    }

    public int abandonExpiredSessions(LocalDate today) {
        var params = new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("inProgressStatus", SessionStatus.IN_PROGRESS.name())
                .addValue("abandonedStatus", SessionStatus.ABANDONED.name());
        return jdbc.update(ABANDON_EXPIRED_SESSIONS, params);
    }

    public int insertParticipation(Long userId, LocalDate challengeDate, String challengeLevel, long sessionId) {
        long id = TSID.Factory.getTsid().toLong();
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("challengeDate", challengeDate)
                .addValue("challengeLevel", challengeLevel)
                .addValue("sessionId", sessionId);
        return jdbc.update(INSERT_PARTICIPATION, params);
    }

    public boolean existsParticipation(Long userId, LocalDate challengeDate) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("challengeDate", challengeDate);
        return Boolean.TRUE.equals(jdbc.queryForObject(EXISTS_PARTICIPATION, params, Boolean.class));
    }

    private static final String AWARD_SESSION_XP = """
            SELECT * FROM award_session_xp(
                :userId, :baseXp, :won, :levelDivisor,
                :maxBonus, :maxDays, :curvePower,
                :freezeInterval, :freezeMax
            )
            """;


    public AwardResult awardSessionXp(Long userId, int baseXp, boolean won,
                                       int levelDivisor, int maxBonus, int maxDays,
                                       double curvePower, int freezeInterval, int freezeMax) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("baseXp", baseXp)
                .addValue("won", won)
                .addValue("levelDivisor", levelDivisor)
                .addValue("maxBonus", maxBonus)
                .addValue("maxDays", maxDays)
                .addValue("curvePower", curvePower)
                .addValue("freezeInterval", freezeInterval)
                .addValue("freezeMax", freezeMax);
        return jdbc.queryForObject(AWARD_SESSION_XP, params, (rs, rowNum) -> new AwardResult(
                rs.getInt("new_streak"),
                rs.getInt("new_best_streak"),
                rs.getDouble("streak_bonus_percent"),
                rs.getInt("streak_bonus_xp"),
                rs.getInt("total_xp_awarded"),
                rs.getInt("freezes_earned"),
                rs.getInt("new_level"),
                rs.getInt("new_freezes")
        ));
    }

    public int getHintsPerCountry() {
        var params = new MapSqlParameterSource("key", "hints_per_country");
        return jdbc.query(GET_SETTING, params, (rs, rowNum) -> rs.getString("value"))
                .stream()
                .findFirst()
                .map(Integer::parseInt)
                .orElse(DEFAULT_HINTS_PER_COUNTRY);
    }

    private static final String FIND_SESSION_STREAK_DATA = """
            SELECT streak_bonus_percent, streak_bonus_xp
            FROM game_sessions WHERE id = :sessionId
            """;

    public Optional<SessionStreakData> findSessionStreakData(long sessionId) {
        var params = new MapSqlParameterSource("sessionId", sessionId);
        return jdbc.query(FIND_SESSION_STREAK_DATA, params, (rs, rowNum) ->
                        new SessionStreakData(rs.getDouble("streak_bonus_percent"), rs.getInt("streak_bonus_xp")))
                .stream()
                .findFirst();
    }

    public Optional<SessionRow> findById(long sessionId) {
        var params = new MapSqlParameterSource("sessionId", sessionId);
        return Optional.ofNullable(jdbc.query(findByIdSql, params, SESSION_EXTRACTOR));
    }

    public Optional<SessionRow> findTodaySessionForUser(Long userId) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("today", LocalDate.now(clock));
        return Optional.ofNullable(jdbc.query(findTodaySessionSql, params, SESSION_EXTRACTOR));
    }

    public Optional<SessionSummaryRow> findTodaySummaryForUser(Long userId, LocalDate today) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("today", today);
        return jdbc.query(findTodaySummaryForUserSql, params, (rs, rowNum) -> toSummaryRow(rs))
                .stream()
                .findFirst();
    }

    public Optional<GuestProgressRow> findGuestProgressForUser(Long userId) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId);
        return jdbc.query(findGuestProgressForUserSql, params, (rs, rowNum) -> new GuestProgressRow(
                        rs.getInt("play_count"),
                        rs.getInt("budget_play_count"),
                        rs.getLong("total_xp"),
                        rs.getInt("current_level"),
                        rs.getInt("level_divisor"),
                        rs.getInt("current_streak"),
                        rs.getInt("best_streak"),
                        rs.getInt("badges_earned"),
                        rs.getTimestamp("expires_at").toInstant()))
                .stream()
                .findFirst();
    }

    public List<GuessAttemptRow> findGuessAttemptsForSession(long sessionId) {
        var params = new MapSqlParameterSource("sessionId", sessionId);
        return jdbc.query(findGuessAttemptsForSessionSql, params, (rs, rowNum) -> new GuessAttemptRow(
                rs.getLong("session_country_id"),
                rs.getInt("attempt_number"),
                rs.getInt("hints_at_guess"),
                rs.getBoolean("is_correct"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("guessed_country_id"),
                rs.getString("iso_alpha2"),
                rs.getString("name_en"),
                rs.getString("flag_emoji"),
                rs.getString("flag_url"),
                rs.getString("region")));
    }

    public CommunityStatsRow findTodayCommunityStats(String challengeLevel, LocalDate today) {
        var params = new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("challengeLevel", challengeLevel)
                .addValue("completedStatus", SessionStatus.COMPLETED.name());
        return jdbc.queryForObject(findTodayCommunityStatsSql, params, (rs, rowNum) -> new CommunityStatsRow(
                rs.getInt("completed_sessions_count"),
                rs.getInt("avg_xp_per_completed_run"),
                rs.getDouble("accuracy_rate"),
                rs.getLong("avg_active_time_per_session_ms"),
                rs.getDouble("avg_attempts_per_country"),
                rs.getDouble("avg_hints_per_country")));
    }

    public Optional<HardestCountryRow> findTodayHardestCountry(String challengeLevel, LocalDate today, int minSamples) {
        var params = new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("challengeLevel", challengeLevel)
                .addValue("completedStatus", SessionStatus.COMPLETED.name())
                .addValue("minSamples", minSamples);
        return jdbc.query(findTodayHardestCountrySql, params, (rs, rowNum) -> new HardestCountryRow(
                        rs.getLong("country_id"),
                        rs.getString("iso_alpha2"),
                        rs.getString("name_en"),
                        rs.getString("flag_emoji"),
                        rs.getString("flag_url"),
                        rs.getString("region"),
                        rs.getDouble("success_rate"),
                        rs.getDouble("avg_attempts_needed"),
                        rs.getDouble("avg_hints_needed"),
                        rs.getLong("avg_active_time_ms"),
                        (Long) rs.getObject("confused_country_id"),
                        rs.getString("confused_iso_alpha2"),
                        rs.getString("confused_name_en"),
                        rs.getString("confused_flag_emoji"),
                        rs.getString("confused_flag_url"),
                        rs.getString("confused_region")))
                .stream()
                .findFirst();
    }

    private static SessionSummaryRow toSummaryRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SessionSummaryRow(
                rs.getLong("session_id"),
                rs.getString("challenge_level"),
                SessionStatus.valueOf(rs.getString("status")),
                rs.getInt("current_country_idx"),
                rs.getInt("total_countries"),
                rs.getInt("guessed_countries"),
                rs.getInt("total_hints_revealed"),
                rs.getInt("total_attempts_used"),
                rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                rs.getLong("active_time_ms"),
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                rs.getInt("total_xp_earned"));
    }

    private static final ResultSetExtractor<SessionRow> SESSION_EXTRACTOR =
            rs -> {
                SessionRow session = null;
                Map<Long, SessionCountryRow> countriesMap = new LinkedHashMap<>();

                while (rs.next()) {
                    if (session == null) {
                        session = new SessionRow(
                                rs.getLong("session_id"),
                                (Long) rs.getObject("user_id"),
                                rs.getString("guest_token"),
                                rs.getLong("challenge_id"),
                                rs.getObject("challenge_date", LocalDate.class),
                                rs.getString("challenge_level"),
                                SessionStatus.valueOf(rs.getString("status")),
                                rs.getInt("current_country_idx"),
                                rs.getTimestamp("started_at") != null
                                        ? rs.getTimestamp("started_at").toInstant() : null,
                                rs.getTimestamp("last_activity_at") != null
                                        ? rs.getTimestamp("last_activity_at").toInstant() : null,
                                rs.getTimestamp("completed_at") != null
                                        ? rs.getTimestamp("completed_at").toInstant() : null,
                                rs.getInt("total_xp_earned"),
                                rs.getInt("max_hints_per_country"),
                                rs.getInt("max_attempts"),
                                rs.getLong("xp_before"),
                                rs.getInt("level_before"),
                                rs.getString("terms_version_accepted"),
                                rs.getString("privacy_version_accepted"),
                                rs.getString("hints_json"),
                                new ArrayList<>());
                    }

                    long scId = rs.getLong("sc_id");
                    if (!countriesMap.containsKey(scId)) {
                        countriesMap.put(scId, new SessionCountryRow(
                                scId,
                                rs.getLong("country_id"),
                                rs.getInt("country_order"),
                                rs.getInt("total_hints"),
                                rs.getInt("hints_revealed"),
                                rs.getInt("attempts_used"),
                                rs.getBoolean("is_guessed"),
                                rs.getLong("time_spent_ms"),
                                rs.getInt("xp_earned"),
                                rs.getString("iso_alpha2"),
                                rs.getString("name_en"),
                                rs.getString("flag_emoji"),
                                rs.getString("flag_url"),
                                rs.getString("region")));
                    }
                }

                if (session != null) {
                    session.countries().addAll(countriesMap.values());
                }
                return session;
            };

    public record SessionSummaryRow(
            long id,
            String challengeLevel,
            SessionStatus status,
            int currentCountryIdx,
            int totalCountries,
            int guessedCountries,
            int totalHintsRevealed,
            int totalAttemptsUsed,
            Instant startedAt,
            long activeTimeMs,
            Instant completedAt,
            int totalXpEarned) {
    }

    public record GuessAttemptRow(
            long sessionCountryId,
            int attemptNumber,
            int hintsAtGuess,
            boolean correct,
            Instant createdAt,
            long guessedCountryId,
            String isoAlpha2,
            String nameEn,
            String flagEmoji,
            String flagUrl,
            String region) {
    }

    public record CommunityStatsRow(
            int completedSessionsCount,
            int avgXpPerCompletedRun,
            double accuracyRate,
            long avgActiveTimePerSessionMs,
            double avgAttemptsPerCountry,
            double avgHintsPerCountry) {
    }

    public record HardestCountryRow(
            long countryId,
            String isoAlpha2,
            String nameEn,
            String flagEmoji,
            String flagUrl,
            String region,
            double successRate,
            double avgAttemptsNeeded,
            double avgHintsNeeded,
            long avgActiveTimeMs,
            Long confusedCountryId,
            String confusedIsoAlpha2,
            String confusedNameEn,
            String confusedFlagEmoji,
            String confusedFlagUrl,
            String confusedRegion) {
    }
}
