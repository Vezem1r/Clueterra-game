package io.clueterra.api.budget.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.budget.dto.BudgetDifficulty;
import io.clueterra.api.budget.dto.BudgetQuestionPromptDto;
import io.clueterra.api.budget.model.BudgetAnswerChoice;
import io.clueterra.api.budget.model.BudgetChallengeQuestionRow;
import io.clueterra.api.budget.model.BudgetGameAnswerRow;
import io.clueterra.api.budget.model.BudgetGameHintRow;
import io.clueterra.api.budget.model.BudgetGameSessionRow;
import io.clueterra.api.budget.model.BudgetGameSessionStatus;
import io.clueterra.api.budget.model.BudgetGuestClaimRow;
import io.clueterra.api.budget.model.BudgetHintMutationRow;
import io.clueterra.api.budget.model.BudgetQuestionImageRow;
import io.clueterra.api.budget.model.BudgetQuestionType;
import io.clueterra.api.budget.model.BudgetSessionMutationRow;
import io.clueterra.api.budget.model.BudgetTodayChallengeRow;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BudgetGameSessionJdbcRepository {

    private static final String FIND_TODAY_CHALLENGE = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "findTodayChallenge");
    private static final String START_SESSION = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "startSession");
    private static final String FIND_SESSION = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "findSession");
    private static final String FIND_TODAY_SESSION = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "findTodaySession");
    private static final String FIND_CHALLENGE_QUESTIONS = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "findChallengeQuestions");
    private static final String FIND_ANSWERS = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "findAnswers");
    private static final String FIND_REVEALED_HINTS = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "findRevealedHints");
    private static final String SUBMIT_ANSWER = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "submitAnswer");
    private static final String REVEAL_HINT = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "revealHint");
    private static final String FIND_GUEST_TOKEN = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "findGuestToken");
    private static final String CLAIM_GUEST_PROGRESS = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "claimGuestProgress");
    private static final String FINALIZE_CONVERTED_GUEST_PROGRESS = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "finalizeConvertedGuestProgress");
    private static final String ABANDON_EXPIRED_SESSIONS = SqlLoader.load(BudgetGameSessionJdbcRepository.class, "abandonExpiredSessions");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public Optional<BudgetTodayChallengeRow> findTodayChallenge() {
        return jdbc.query(FIND_TODAY_CHALLENGE, Map.of(), this::mapTodayChallenge).stream().findFirst();
    }

    public BudgetSessionMutationRow startSession(
            long sessionId,
            long userId,
            int baseXp,
            int fullXpThresholdPercent,
            double curvePower,
            int levelDivisor) {
        var parameters = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("userId", userId)
                .addValue("baseXp", baseXp)
                .addValue("fullXpThresholdPercent", fullXpThresholdPercent)
                .addValue("curvePower", curvePower)
                .addValue("levelDivisor", levelDivisor);
        return jdbc.query(START_SESSION, parameters, this::mapMutation).stream()
                .findFirst()
                .orElse(new BudgetSessionMutationRow("INVALID_REQUEST", null, null, null));
    }

    public Optional<BudgetGameSessionRow> findSession(long sessionId) {
        return findSession(FIND_SESSION, Map.of("sessionId", sessionId));
    }

    public Optional<BudgetGameSessionRow> findTodaySession(long userId) {
        return findSession(FIND_TODAY_SESSION, Map.of("userId", userId));
    }

    public List<BudgetChallengeQuestionRow> findChallengeQuestions(long challengeId) {
        return jdbc.query(
                FIND_CHALLENGE_QUESTIONS, Map.of("challengeId", challengeId), this::mapQuestion);
    }

    public List<BudgetGameAnswerRow> findAnswers(long sessionId) {
        return jdbc.query(FIND_ANSWERS, Map.of("sessionId", sessionId), this::mapAnswer);
    }

    public List<BudgetGameHintRow> findRevealedHints(long sessionId) {
        return jdbc.query(
                FIND_REVEALED_HINTS, Map.of("sessionId", sessionId), this::mapHint);
    }

    public BudgetHintMutationRow revealHint(
            long sessionId, long userId, long variantId, int hintIndex) {
        var parameters = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("userId", userId)
                .addValue("variantId", variantId)
                .addValue("hintIndex", hintIndex);
        return jdbc.query(REVEAL_HINT, parameters, this::mapHintMutation).stream()
                .findFirst()
                .orElse(new BudgetHintMutationRow(
                        "INVALID_REQUEST", null, null, null, null));
    }

    public BudgetSessionMutationRow submitAnswer(
            long answerId,
            long sessionId,
            long userId,
            long variantId,
            BigDecimal guessedValue,
            BudgetAnswerChoice guessedChoice) {
        var parameters = new MapSqlParameterSource()
                .addValue("answerId", answerId)
                .addValue("sessionId", sessionId)
                .addValue("userId", userId)
                .addValue("variantId", variantId)
                .addValue("guessedValue", guessedValue)
                .addValue("guessedChoice", guessedChoice == null ? null : guessedChoice.name());
        return jdbc.query(SUBMIT_ANSWER, parameters, this::mapMutation).stream()
                .findFirst()
                .orElse(new BudgetSessionMutationRow("INVALID_REQUEST", sessionId, variantId, null));
    }

    public Optional<String> findGuestToken(long sessionId) {
        return jdbc.query(
                        FIND_GUEST_TOKEN,
                        Map.of("sessionId", sessionId),
                        (resultSet, rowNumber) -> resultSet.getString("guest_token"))
                .stream()
                .findFirst();
    }

    public BudgetGuestClaimRow claimGuestProgress(String guestToken, long targetUserId) {
        var parameters = new MapSqlParameterSource()
                .addValue("guestToken", guestToken)
                .addValue("targetUserId", targetUserId);
        return jdbc.query(
                        CLAIM_GUEST_PROGRESS,
                        parameters,
                        (resultSet, rowNumber) -> new BudgetGuestClaimRow(
                                resultSet.getString("claim_status"),
                                resultSet.getObject("guest_user_id", Long.class),
                                resultSet.getObject("target_user_id", Long.class),
                                resultSet.getObject("session_id", Long.class),
                                resultSet.getString("session_status")))
                .stream()
                .findFirst()
                .orElse(new BudgetGuestClaimRow("NO_GUEST", null, targetUserId, null, null));
    }

    public boolean finalizeConvertedGuestProgress(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                FINALIZE_CONVERTED_GUEST_PROGRESS, Map.of("userId", userId), Boolean.class));
    }

    public int abandonExpiredSessions() {
        Integer updated = jdbc.queryForObject(ABANDON_EXPIRED_SESSIONS, Map.of(), Integer.class);
        return updated == null ? 0 : updated;
    }

    private Optional<BudgetGameSessionRow> findSession(String sql, Map<String, ?> parameters) {
        return jdbc.query(sql, parameters, this::mapSession).stream().findFirst();
    }

    private BudgetTodayChallengeRow mapTodayChallenge(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new BudgetTodayChallengeRow(
                resultSet.getObject("challenge_date", java.time.LocalDate.class),
                resultSet.getInt("question_count"));
    }

    private BudgetGameSessionRow mapSession(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BudgetGameSessionRow(
                resultSet.getLong("id"),
                resultSet.getLong("user_id"),
                resultSet.getBoolean("is_guest"),
                resultSet.getLong("challenge_id"),
                resultSet.getObject("challenge_date", java.time.LocalDate.class),
                BudgetGameSessionStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("current_position"),
                resultSet.getInt("total_xp_earned"),
                resultSet.getInt("streak_bonus_xp"),
                resultSet.getInt("base_xp_per_question"),
                resultSet.getInt("full_xp_threshold_percent"),
                resultSet.getLong("xp_before"),
                resultSet.getInt("level_before"),
                instant(resultSet, "started_at"),
                instant(resultSet, "completed_at"));
    }

    private BudgetChallengeQuestionRow mapQuestion(ResultSet resultSet, int rowNumber)
            throws SQLException {
        BudgetQuestionType type = BudgetQuestionType.valueOf(resultSet.getString("question_type"));
        return new BudgetChallengeQuestionRow(
                resultSet.getLong("variant_id"),
                resultSet.getInt("position"),
                type,
                BudgetDifficulty.valueOf(resultSet.getString("difficulty")),
                prompt(resultSet.getString("prompt_payload"), type),
                resultSet.getInt("available_hint_count"),
                image(resultSet, "primary"),
                image(resultSet, "comparison"));
    }

    private BudgetGameAnswerRow mapAnswer(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BudgetGameAnswerRow(
                resultSet.getLong("question_variant_id"),
                resultSet.getInt("position"),
                resultSet.getBigDecimal("guessed_value"),
                choice(resultSet.getString("guessed_choice")),
                resultSet.getBigDecimal("answer_value"),
                resultSet.getString("answer_unit"),
                choice(resultSet.getString("answer_choice")),
                resultSet.getBigDecimal("accuracy_percent"),
                resultSet.getBigDecimal("raw_xp_percent"),
                resultSet.getInt("raw_xp_earned"),
                resultSet.getBigDecimal("hint_multiplier_percent"),
                resultSet.getInt("hints_used"),
                resultSet.getBigDecimal("xp_percent"),
                resultSet.getInt("xp_earned"),
                json(resultSet.getString("reveal_payload")),
                instant(resultSet, "answered_at"));
    }

    private BudgetGameHintRow mapHint(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BudgetGameHintRow(
                resultSet.getLong("question_variant_id"),
                resultSet.getInt("position"),
                resultSet.getInt("hint_index"),
                resultSet.getBigDecimal("multiplier_percent"),
                json(resultSet.getString("hint_payload")),
                instant(resultSet, "revealed_at"));
    }

    private BudgetHintMutationRow mapHintMutation(ResultSet resultSet, int rowNumber)
            throws SQLException {
        String payload = resultSet.getString("hint_payload");
        return new BudgetHintMutationRow(
                resultSet.getString("outcome"),
                resultSet.getObject("hint_index", Integer.class),
                payload == null ? null : json(payload),
                resultSet.getBigDecimal("effective_multiplier_percent"),
                instant(resultSet, "revealed_at"));
    }

    private BudgetSessionMutationRow mapMutation(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new BudgetSessionMutationRow(
                resultSet.getString("outcome"),
                resultSet.getObject("session_id", Long.class),
                resultSet.getObject("question_variant_id", Long.class),
                resultSet.getObject("answer_position", Integer.class));
    }

    private BudgetQuestionImageRow image(ResultSet resultSet, String prefix) throws SQLException {
        String objectKey = resultSet.getString(prefix + "_image_object_key");
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return new BudgetQuestionImageRow(
                objectKey,
                resultSet.getString(prefix + "_image_alt_text"),
                resultSet.getString(prefix + "_image_author_name"),
                resultSet.getString(prefix + "_image_attribution_text"),
                resultSet.getString(prefix + "_image_license_label"),
                resultSet.getString(prefix + "_image_license_url"),
                resultSet.getString(prefix + "_image_source_page_url"));
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid BudgetGuessr JSON payload", exception);
        }
    }

    private BudgetQuestionPromptDto prompt(String value, BudgetQuestionType type) {
        try {
            BudgetQuestionPromptDto prompt = objectMapper.readerFor(BudgetQuestionPromptDto.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(value);
            return prompt.validateFor(type);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid BudgetGuessr prompt payload", exception);
        }
    }

    private BudgetAnswerChoice choice(String value) {
        return value == null ? null : BudgetAnswerChoice.valueOf(value);
    }

    private java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
