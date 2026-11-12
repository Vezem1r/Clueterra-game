package io.clueterra.api.budget.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.TestcontainersConfiguration;
import io.clueterra.api.achievements.engine.BadgeEngineJdbcRepository;
import io.clueterra.api.budget.model.BudgetAnswerChoice;
import io.clueterra.api.budget.model.BudgetGameAnswerRow;
import io.clueterra.api.budget.model.BudgetGameSessionStatus;
import io.clueterra.api.budget.model.BudgetSessionMutationRow;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;

@JdbcTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.schemas=cl_api_schema",
        "spring.flyway.default-schema=cl_api_schema",
        "spring.flyway.create-schemas=true",
        "spring.datasource.hikari.connection-init-sql=SET search_path TO cl_api_schema, cl_schema"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class BudgetGameSessionIntegrationTest {

    private static final long CHALLENGE_ID = 301L;
    private static final long SESSION_ID = 401L;
    private static final long USER_ID = 1L;
    private static final int FIRST_STREAK_DAY_BONUS_XP = 23;

    private static final String TRUNCATE_TEST_DATA = """
            TRUNCATE
                cl_api_schema.users,
                cl_budget_schema.daily_challenge,
                cl_budget_schema.question_variant,
                cl_budget_schema.question,
                cl_budget_schema.project,
                cl_media_schema.image_resource
            CASCADE
            """;

    private static final String INSERT_USER = """
            INSERT INTO cl_api_schema.users (
                id, email, username, password_hash, role, enabled, is_guest,
                guest_token, guest_token_expires_at
            )
            VALUES (
                :id, :email, :username, 'hash', 'USER', TRUE, :guest,
                :guestToken, CASE WHEN :guest THEN NOW() + INTERVAL '1 day' ELSE NULL END
            )
            """;

    private static final String INSERT_PROFILE = """
            INSERT INTO cl_api_schema.user_profiles (user_id, total_xp, current_level)
            VALUES (:userId, :totalXp, :level)
            """;

    private static final String INSERT_QUESTION = """
            INSERT INTO cl_budget_schema.question (
                id, project_id, country_code, region_name, category, answer_cost_usd,
                image_alt_text, image_resource_id
            )
            VALUES (
                :id, :projectId, :countryCode, :regionName, 'BRIDGE', 100000000,
                :altText, :imageResourceId
            )
            """;

    private static final String INSERT_IMAGE_RESOURCE = """
            INSERT INTO cl_media_schema.image_resource (
                id, provider, source_key, lifecycle_status, validation_status
            )
            VALUES (:id, 'WIKIMEDIA_COMMONS', :sourceKey, 'ACTIVE', 'AVAILABLE')
            """;

    private static final String INSERT_IMAGE_RIGHTS = """
            INSERT INTO cl_media_schema.image_rights_revision (
                id, image_resource_id, original_url, download_url, source_page_url,
                author_name, attribution_text, license_label, license_url, mime_type
            )
            VALUES (
                :id, :id, 'https://upload.wikimedia.org/test.jpg',
                'https://upload.wikimedia.org/test.jpg',
                'https://commons.wikimedia.org/wiki/File:Test.jpg',
                'Test author', 'Test attribution', 'CC BY-SA 4.0',
                'https://creativecommons.org/licenses/by-sa/4.0/', 'image/jpeg'
            )
            """;

    private static final String SET_CURRENT_IMAGE_RIGHTS = """
            UPDATE cl_media_schema.image_resource
            SET current_rights_revision_id = :id
            WHERE id = :id
            """;

    private static final String INSERT_IMAGE_RENDITION = """
            INSERT INTO cl_media_schema.image_rendition (
                id, image_resource_id, rights_revision_id, storage_bucket,
                object_key, rendition_type, mime_type, storage_status
            )
            VALUES (
                :id, :id, :id, 'budget-images', :objectKey,
                'DISPLAY', 'image/jpeg', 'AVAILABLE'
            )
            """;

    private static final String INSERT_PROJECT = """
            INSERT INTO cl_budget_schema.project (id) VALUES (:id)
            """;

    private static final String INSERT_VARIANT = """
            INSERT INTO cl_budget_schema.question_variant (
                id, source_question_id, question_type, answer_value, answer_unit,
                prompt_payload, reveal_payload, difficulty
            )
            VALUES (
                :id, :questionId, 'HOW_MUCH', 100000000, 'USD',
                CAST(:prompt AS JSONB),
                '{"actualCost":100000000,"hints":[{"type":"COMPARABLE_PROJECT","multiplierPercent":85},{"type":"ANSWER_RANGE","multiplierPercent":60}]}'::JSONB,
                'EASY'
            )
            """;

    private static final String INSERT_CHALLENGE = """
            INSERT INTO cl_budget_schema.daily_challenge (id, challenge_date, status)
            VALUES (301, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE, 'PUBLISHED')
            """;

    private static final String INSERT_CHALLENGE_QUESTION = """
            INSERT INTO cl_budget_schema.daily_challenge_question (
                challenge_id, position, question_variant_id
            )
            VALUES (301, :position, :variantId)
            """;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private BudgetGameSessionJdbcRepository repository;
    private BadgeEngineJdbcRepository badgeRepository;
    private GameSessionJdbcRepository gameSessionRepository;

    @BeforeEach
    void setUp() {
        repository = new BudgetGameSessionJdbcRepository(
                jdbc, new ObjectMapper().findAndRegisterModules());
        badgeRepository = new BadgeEngineJdbcRepository(jdbc);
        gameSessionRepository = new GameSessionJdbcRepository(jdbc, Clock.systemUTC());
        jdbc.getJdbcTemplate().execute(TRUNCATE_TEST_DATA);
        seedChallenge();
    }

    @Test
    @DisplayName("should enforce question order, idempotency and single XP application")
    void shouldCompleteSessionAtomically() {
        insertUser(USER_ID, false, null);
        insertProfile(USER_ID, 200, 2);

        var started = startSession(SESSION_ID, USER_ID);
        var existing = startSession(402L, USER_ID);

        assertThat(started.outcome()).isEqualTo("STARTED");
        assertThat(existing.outcome()).isEqualTo("EXISTING");
        assertThat(existing.sessionId()).isEqualTo(SESSION_ID);
        assertThat(answer(501L, SESSION_ID, USER_ID, 202L, "100000000"))
                .isEqualTo("STALE_QUESTION");
        assertThat(answer(502L, SESSION_ID, USER_ID, 201L, "100000000"))
                .isEqualTo("ANSWERED");
        assertThat(answer(503L, SESSION_ID, USER_ID, 201L, "1"))
                .isEqualTo("ALREADY_ANSWERED");

        for (int position = 2; position <= 5; position++) {
            assertThat(answer(
                    510L + position, SESSION_ID, USER_ID, 200L + position, "100000000"))
                    .isEqualTo("ANSWERED");
        }

        var session = repository.findSession(SESSION_ID).orElseThrow();
        assertThat(session.status()).isEqualTo(BudgetGameSessionStatus.COMPLETED);
        assertThat(session.currentPosition()).isEqualTo(6);
        assertThat(session.totalXpEarned()).isEqualTo(500);
        assertThat(repository.findAnswers(SESSION_ID))
                .hasSize(5)
                .extracting(BudgetGameAnswerRow::xpEarned)
                .containsOnly(100);
        assertThat(session.streakBonusXp()).isEqualTo(FIRST_STREAK_DAY_BONUS_XP);
        assertThat(totalXp(USER_ID)).isEqualTo(700 + FIRST_STREAK_DAY_BONUS_XP);
        assertThat(budgetStat(USER_ID, "completed_challenges")).isEqualTo(1);
        assertThat(budgetStat(USER_ID, "answered_questions")).isEqualTo(5);
        assertThat(budgetStat(USER_ID, "exact_guesses")).isEqualTo(5);
        assertThat(budgetStat(USER_ID, "clean_sweeps")).isEqualTo(1);
        assertThat(budgetStat(USER_ID, "unique_projects_explored")).isEqualTo(5);
        assertThat(budgetStat(USER_ID, "unique_projects_mastered")).isEqualTo(5);
        assertThat(budgetAmount(USER_ID, "mastered_project_value_usd"))
                .isEqualByComparingTo("500000000");
        assertThat(repository.finalizeConvertedGuestProgress(USER_ID)).isFalse();
        assertThat(totalXp(USER_ID)).isEqualTo(700 + FIRST_STREAK_DAY_BONUS_XP);
    }

    @Test
    @DisplayName("should keep a temporarily quarantined image available to today's published challenge")
    void shouldServeTemporarilyQuarantinedImageForToday() {
        jdbc.update(
                """
                        UPDATE cl_media_schema.image_resource
                        SET lifecycle_status = 'QUARANTINED',
                            validation_status = 'SOURCE_MISSING'
                        WHERE id = 101
                        """,
                Map.of());

        var questions = repository.findChallengeQuestions(CHALLENGE_ID);

        assertThat(questions).hasSize(5);
        assertThat(questions.getFirst().primaryImage().objectKey())
                .isEqualTo("budget/test/1.jpg");
    }

    @ParameterizedTest(name = "guess {0} should yield {1}% accuracy and {2} XP")
    @CsvSource({
            "100000000, 100.00, 100.00, 100",
            "120000000, 83.33, 100.00, 100",
            "125000000, 80.00, 95.27, 95",
            "200000000, 50.00, 0.00, 0"
    })
    @DisplayName("should apply the configured numeric XP curve")
    void shouldScoreNumericGuess(
            String guess, String expectedAccuracy, String expectedXpPercent, int expectedXp) {
        insertUser(USER_ID, false, null);
        insertProfile(USER_ID, 0, 1);
        startSession(SESSION_ID, USER_ID);

        assertThat(answer(501L, SESSION_ID, USER_ID, 201L, guess)).isEqualTo("ANSWERED");

        BudgetGameAnswerRow answer = repository.findAnswers(SESSION_ID).getFirst();
        assertThat(answer.accuracyPercent()).isEqualByComparingTo(expectedAccuracy);
        assertThat(answer.xpPercent()).isEqualByComparingTo(expectedXpPercent);
        assertThat(answer.xpEarned()).isEqualTo(expectedXp);
    }

    @Test
    @DisplayName("should reveal hints in order, persist them and apply the XP penalty atomically")
    void shouldPersistHintPenalty() {
        insertUser(USER_ID, false, null);
        insertProfile(USER_ID, 0, 1);
        startSession(SESSION_ID, USER_ID);

        var stale = repository.revealHint(SESSION_ID, USER_ID, 201L, 1);
        var revealed = repository.revealHint(SESSION_ID, USER_ID, 201L, 0);
        var replayed = repository.revealHint(SESSION_ID, USER_ID, 201L, 0);

        assertThat(stale.outcome()).isEqualTo("STALE_HINT");
        assertThat(revealed.outcome()).isEqualTo("REVEALED");
        assertThat(revealed.effectiveMultiplierPercent()).isEqualByComparingTo("85");
        assertThat(replayed.outcome()).isEqualTo("ALREADY_REVEALED");
        assertThat(repository.findRevealedHints(SESSION_ID))
                .singleElement()
                .satisfies(hint -> {
                    assertThat(hint.index()).isZero();
                    assertThat(hint.multiplierPercent()).isEqualByComparingTo("85");
                });

        assertThat(answer(501L, SESSION_ID, USER_ID, 201L, "100000000"))
                .isEqualTo("ANSWERED");
        BudgetGameAnswerRow answer = repository.findAnswers(SESSION_ID).getFirst();
        assertThat(answer.rawXpPercent()).isEqualByComparingTo("100");
        assertThat(answer.rawXpEarned()).isEqualTo(100);
        assertThat(answer.hintMultiplierPercent()).isEqualByComparingTo("85");
        assertThat(answer.hintsUsed()).isEqualTo(1);
        assertThat(answer.xpPercent()).isEqualByComparingTo("85");
        assertThat(answer.xpEarned()).isEqualTo(85);
        assertThat(repository.findSession(SESSION_ID).orElseThrow().totalXpEarned()).isEqualTo(85);
    }

    @Test
    @DisplayName("should score an exact zero answer without dividing by zero")
    void shouldScoreExactZeroAnswer() {
        jdbc.update("""
                UPDATE cl_budget_schema.question_variant
                SET question_type = 'HOW_LATE', answer_value = 0, answer_unit = 'MONTHS'
                WHERE id = 201
                """, Map.of());
        insertUser(USER_ID, false, null);
        insertProfile(USER_ID, 0, 1);
        startSession(SESSION_ID, USER_ID);

        assertThat(answer(501L, SESSION_ID, USER_ID, 201L, "0")).isEqualTo("ANSWERED");

        BudgetGameAnswerRow answer = repository.findAnswers(SESSION_ID).getFirst();
        assertThat(answer.accuracyPercent()).isEqualByComparingTo("100");
        assertThat(answer.xpEarned()).isEqualTo(100);
    }

    @ParameterizedTest(name = "choice {0} should yield {1}% accuracy and {2} XP")
    @CsvSource({
            "PRIMARY, 100.00, 100",
            "COMPARISON, 0.00, 0"
    })
    @DisplayName("should score higher-lower choices and reject numeric guesses")
    void shouldScoreHigherLowerChoice(
            BudgetAnswerChoice choice, String expectedAccuracy, int expectedXp) {
        makeFirstQuestionHigherLower();
        insertUser(USER_ID, false, null);
        insertProfile(USER_ID, 0, 1);
        startSession(SESSION_ID, USER_ID);

        var invalid = repository.submitAnswer(
                501L, SESSION_ID, USER_ID, 201L, BigDecimal.ONE, null);
        var answered = repository.submitAnswer(
                502L, SESSION_ID, USER_ID, 201L, null, choice);

        assertThat(invalid.outcome()).isEqualTo("INVALID_ANSWER");
        assertThat(answered.outcome()).isEqualTo("ANSWERED");
        BudgetGameAnswerRow answer = repository.findAnswers(SESSION_ID).getFirst();
        assertThat(answer.accuracyPercent()).isEqualByComparingTo(expectedAccuracy);
        assertThat(answer.xpEarned()).isEqualTo(expectedXp);
    }

    @Test
    @DisplayName("should transfer an in-progress guest session without exposing it to another user")
    void shouldTransferInProgressGuestSession() {
        insertUser(2L, false, null);
        insertProfile(2L, 50, 1);
        insertUser(3L, true, "guest-token");
        startSession(601L, 3L);

        assertThat(answer(602L, 601L, 3L, 201L, "100000000")).isEqualTo("ANSWERED");
        assertThat(answer(603L, 601L, 2L, 202L, "100000000")).isEqualTo("ACCESS_DENIED");

        var claim = repository.claimGuestProgress("guest-token", 2L);

        assertThat(claim.status()).isEqualTo("CLAIMED_IN_PROGRESS");
        assertThat(repository.findSession(601L).orElseThrow().userId()).isEqualTo(2L);
        assertThat(budgetStat(2L, "answered_questions")).isEqualTo(1);
        assertThat(budgetStat(2L, "unique_projects_explored")).isEqualTo(1);
        assertThat(budgetStat(3L, "answered_questions")).isZero();
        assertThat(answer(604L, 601L, 2L, 202L, "100000000")).isEqualTo("ANSWERED");
        assertThat(totalXp(2L)).isEqualTo(50);
    }

    @Test
    @DisplayName("should apply completed guest XP exactly once after registration conversion")
    void shouldFinalizeConvertedGuestXpOnce() {
        insertUser(3L, true, "guest-token");
        startSession(601L, 3L);
        answerAllCorrect();
        convertGuestToRegisteredUser();

        assertThat(repository.finalizeConvertedGuestProgress(3L)).isTrue();
        assertThat(repository.finalizeConvertedGuestProgress(3L)).isFalse();
        assertThat(totalXp(3L)).isEqualTo(500 + FIRST_STREAK_DAY_BONUS_XP);
        assertThat(budgetStat(3L, "completed_challenges")).isEqualTo(1);
        assertThat(budgetStat(3L, "unique_projects_mastered")).isEqualTo(5);
    }

    @Test
    @DisplayName("should include pending BudgetGuessr XP in shared guest progress")
    void shouldIncludePendingBudgetXpInSharedGuestProgress() {
        insertUser(3L, true, "guest-token");
        insertProfile(3L, 400, 3);
        startSession(601L, 3L);
        answerAllCorrect();

        var progress = gameSessionRepository.findGuestProgressForUser(3L).orElseThrow();

        assertThat(progress.playCount()).isZero();
        assertThat(progress.budgetPlayCount()).isEqualTo(1);
        assertThat(progress.totalXp()).isEqualTo(900);
        assertThat(progress.currentLevel()).isEqualTo(4);
        assertThat(totalXp(3L)).isEqualTo(400);
    }

    @Test
    @DisplayName("should transfer the guest's complete BudgetGuessr history and apply pending XP once")
    void shouldTransferCompleteGuestHistory() {
        insertUser(2L, false, null);
        insertProfile(2L, 50, 1);
        insertUser(3L, true, "guest-token");

        startSession(601L, 3L);
        answerAllCorrect();
        moveFirstChallengeToYesterdayAndCreateCurrentChallenge();
        startSession(602L, 3L);
        assertThat(answer(801L, 602L, 3L, 201L, "100000000")).isEqualTo("ANSWERED");

        var claim = repository.claimGuestProgress("guest-token", 2L);

        assertThat(claim.status()).isEqualTo("CLAIMED_IN_PROGRESS");
        assertThat(repository.findSession(601L).orElseThrow().userId()).isEqualTo(2L);
        assertThat(repository.findSession(602L).orElseThrow().userId()).isEqualTo(2L);
        assertThat(totalXp(2L)).isEqualTo(550);
        assertThat(repository.findSession(601L).orElseThrow().streakBonusXp()).isZero();
        assertThat(budgetStat(2L, "completed_challenges")).isEqualTo(1);
        assertThat(budgetStat(2L, "answered_questions")).isEqualTo(6);
        assertThat(budgetStat(2L, "unique_projects_explored")).isEqualTo(5);
        assertThat(budgetStat(2L, "unique_projects_mastered")).isEqualTo(5);
        assertThat(budgetStat(3L, "answered_questions")).isZero();
    }

    @Test
    @DisplayName("should derive all-categories mastery from currently published question categories")
    void shouldEvaluateAllPublishedBudgetCategories() {
        insertUser(USER_ID, false, null);
        insertProfile(USER_ID, 0, 1);
        startSession(SESSION_ID, USER_ID);
        assertThat(answer(501L, SESSION_ID, USER_ID, 201L, "100000000")).isEqualTo("ANSWERED");

        assertThat(badgeRepository.hasMasteredAllBudgetCategories(USER_ID)).isTrue();

        jdbc.update(
                "UPDATE cl_budget_schema.question SET category = 'AIRPORT' WHERE id = 102",
                Map.of());

        assertThat(badgeRepository.hasMasteredAllBudgetCategories(USER_ID)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPromptPayloads")
    @DisplayName("should reject unsafe or mismatched prompt payloads")
    void shouldRejectInvalidPromptPayload(
            String scenario, String questionType, String promptPayload) {
        jdbc.update("""
                UPDATE cl_budget_schema.question_variant
                SET question_type = :questionType,
                    prompt_payload = CAST(:promptPayload AS JSONB)
                WHERE id = 201
                """, Map.of(
                "questionType", questionType,
                "promptPayload", promptPayload));

        assertThatThrownBy(() -> repository.findChallengeQuestions(CHALLENGE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid BudgetGuessr prompt payload");
    }

    private static Stream<Arguments> invalidPromptPayloads() {
        String legacyPrompt = unaryPrompt(1).replace(
                "\"project\": {", "\"answerValue\": 100000000,\n  \"project\": {");
        return Stream.of(
                Arguments.of("legacy answer field", "HOW_MUCH", legacyPrompt),
                Arguments.of("comparison type with unary prompt", "HIGHER_LOWER", unaryPrompt(1)));
    }

    private BudgetSessionMutationRow startSession(long sessionId, long userId) {
        return repository.startSession(sessionId, userId, 100, 20, 0.75, 1000);
    }

    private String answer(
            long answerId, long sessionId, long userId, long variantId, String value) {
        return repository.submitAnswer(
                answerId, sessionId, userId, variantId, new BigDecimal(value), null).outcome();
    }

    private void answerAllCorrect() {
        for (int position = 1; position <= 5; position++) {
            assertThat(answer(
                    700L + position, 601L, 3L, 200L + position, "100000000"))
                    .isEqualTo("ANSWERED");
        }
    }

    private void seedChallenge() {
        jdbc.getJdbcTemplate().execute(INSERT_CHALLENGE);
        for (int position = 1; position <= 5; position++) {
            long questionId = 100L + position;
            long variantId = 200L + position;
            String objectKey = "budget/test/" + position + ".jpg";
            jdbc.update(INSERT_PROJECT, Map.of("id", questionId));
            var mediaParams = new MapSqlParameterSource()
                    .addValue("id", questionId)
                    .addValue("sourceKey", "test-image-" + position)
                    .addValue("objectKey", objectKey);
            jdbc.update(INSERT_IMAGE_RESOURCE, mediaParams);
            jdbc.update(INSERT_IMAGE_RIGHTS, mediaParams);
            jdbc.update(SET_CURRENT_IMAGE_RIGHTS, mediaParams);
            jdbc.update(INSERT_IMAGE_RENDITION, mediaParams);
            jdbc.update(INSERT_QUESTION, new MapSqlParameterSource()
                    .addValue("id", questionId)
                    .addValue("projectId", questionId)
                    .addValue("countryCode", position == 2 ? "US" : "JP")
                    .addValue("regionName", position == 2 ? "NORTH_AMERICA" : "ASIA")
                    .addValue("altText", "Project " + position)
                    .addValue("imageResourceId", questionId));
            jdbc.update(INSERT_VARIANT, new MapSqlParameterSource()
                    .addValue("id", variantId)
                    .addValue("questionId", questionId)
                    .addValue("prompt", unaryPrompt(position)));
            jdbc.update(INSERT_CHALLENGE_QUESTION, new MapSqlParameterSource()
                    .addValue("position", position)
                    .addValue("variantId", variantId));
        }
    }

    private void makeFirstQuestionHigherLower() {
        jdbc.update("""
                UPDATE cl_budget_schema.question_variant
                SET comparison_question_id = 102,
                    question_type = 'HIGHER_LOWER',
                    answer_value = NULL,
                    answer_unit = NULL,
                    answer_choice = 'PRIMARY',
                    prompt_payload = CAST(:prompt AS JSONB)
                WHERE id = 201
                """, Map.of("prompt", comparisonPrompt()));
    }

    private void moveFirstChallengeToYesterdayAndCreateCurrentChallenge() {
        jdbc.getJdbcTemplate().update("""
                UPDATE cl_budget_schema.daily_challenge
                SET challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE - 1
                WHERE id = 301
                """);
        jdbc.getJdbcTemplate().update("""
                INSERT INTO cl_budget_schema.daily_challenge (id, challenge_date, status)
                VALUES (302, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE, 'PUBLISHED')
                """);
        for (int position = 1; position <= 5; position++) {
            jdbc.update("""
                    INSERT INTO cl_budget_schema.daily_challenge_question (
                        challenge_id, position, question_variant_id
                    ) VALUES (302, :position, :variantId)
                    """, new MapSqlParameterSource()
                    .addValue("position", position)
                    .addValue("variantId", 200L + position));
        }
    }

    private void insertUser(long id, boolean guest, String guestToken) {
        jdbc.update(INSERT_USER, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("email", guest ? null : "user" + id + "@example.com")
                .addValue("username", guest ? null : "user" + id)
                .addValue("guest", guest)
                .addValue("guestToken", guestToken));
    }

    private void insertProfile(long userId, long totalXp, int level) {
        jdbc.update(INSERT_PROFILE, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("totalXp", totalXp)
                .addValue("level", level));
    }

    private void convertGuestToRegisteredUser() {
        jdbc.update("""
                UPDATE cl_api_schema.users
                SET email = :email,
                    username = :username,
                    is_guest = FALSE,
                    guest_token = NULL,
                    guest_token_expires_at = NULL
                WHERE id = :userId
                """, new MapSqlParameterSource()
                .addValue("userId", 3L)
                .addValue("email", "user" + 3L + "@example.com")
                .addValue("username", "user" + 3L));
    }

    private long totalXp(long userId) {
        Long totalXp = jdbc.queryForObject(
                "SELECT total_xp FROM cl_api_schema.user_profiles WHERE user_id = :userId",
                Map.of("userId", userId),
                Long.class);
        assertThat(totalXp).isNotNull();
        return totalXp;
    }

    private int budgetStat(long userId, String column) {
        Integer value = jdbc.queryForObject(
                "SELECT " + column + " FROM cl_api_schema.user_budget_stats WHERE user_id = :userId",
                Map.of("userId", userId),
                Integer.class);
        assertThat(value).isNotNull();
        return value;
    }

    private BigDecimal budgetAmount(long userId, String column) {
        BigDecimal value = jdbc.queryForObject(
                "SELECT " + column + " FROM cl_api_schema.user_budget_stats WHERE user_id = :userId",
                Map.of("userId", userId),
                BigDecimal.class);
        assertThat(value).isNotNull();
        return value;
    }

    private static String unaryPrompt(int position) {
        return """
                {
                  "project": {
                    "name": "Project %d",
                    "countryCode": "JP",
                    "countryName": "Japan",
                    "regionName": "ASIA",
                    "category": "BRIDGE",
                    "completionYear": 2020
                  }
                }
                """.formatted(position);
    }

    private static String comparisonPrompt() {
        return """
                {
                  "primaryProject": {
                    "name": "Project 1",
                    "countryCode": "JP",
                    "countryName": "Japan",
                    "regionName": "ASIA",
                    "category": "BRIDGE",
                    "completionYear": 2020
                  },
                  "comparisonProject": {
                    "name": "Project 2",
                    "countryCode": "US",
                    "countryName": "United States",
                    "regionName": "NORTH_AMERICA",
                    "category": "BRIDGE",
                    "completionYear": 2021
                  }
                }
                """;
    }
}
