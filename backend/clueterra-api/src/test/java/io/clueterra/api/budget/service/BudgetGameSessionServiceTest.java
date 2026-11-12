package io.clueterra.api.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.budget.dto.BudgetCategory;
import io.clueterra.api.budget.dto.BudgetDifficulty;
import io.clueterra.api.budget.dto.BudgetPromptProjectDto;
import io.clueterra.api.budget.dto.BudgetQuestionPromptDto;
import io.clueterra.api.budget.exception.BudgetGameException;
import io.clueterra.api.budget.model.BudgetChallengeQuestionRow;
import io.clueterra.api.budget.model.BudgetGameAnswerRow;
import io.clueterra.api.budget.model.BudgetGameSessionRow;
import io.clueterra.api.budget.model.BudgetGameSessionStatus;
import io.clueterra.api.budget.model.BudgetQuestionType;
import io.clueterra.api.budget.repository.BudgetGameSessionJdbcRepository;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class BudgetGameSessionServiceTest {

    private static final long USER_ID = 1L;
    private static final long SESSION_ID = 401L;
    private static final long CHALLENGE_ID = 301L;
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    @Mock
    private BudgetGameSessionJdbcRepository repository;
    @Mock
    private SettingsService settingsService;
    @Mock
    private AuthJdbcRepository authRepository;
    @Mock
    private LegalAcceptanceService legalAcceptanceService;
    @Mock
    private StorageService storageService;
    @Mock
    private TemplateBadgeEngine templateBadgeEngine;

    private ObjectMapper objectMapper;
    private BudgetGameSessionService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new BudgetGameSessionService(
                repository, settingsService, authRepository, legalAcceptanceService,
                storageService, templateBadgeEngine, new BudgetRevealPayloadSanitizer(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("should expose answered questions and the current question without future prompts")
    void shouldExposeOnlyPlayableSessionState() throws Exception {
        when(repository.findSession(SESSION_ID)).thenReturn(Optional.of(session(TODAY)));
        when(repository.findChallengeQuestions(CHALLENGE_ID)).thenReturn(questions());
        when(repository.findAnswers(SESSION_ID)).thenReturn(List.of(answer()));

        var result = service.getSession(SESSION_ID, USER_ID);
        String responseJson = objectMapper.writeValueAsString(result);

        assertThat(result.answeredQuestions())
                .singleElement()
                .satisfies(answered -> assertThat(answered.question().variantId()).isEqualTo(201L));
        assertThat(result.currentQuestion().variantId()).isEqualTo(202L);
        assertThat(responseJson)
                .contains("Project 1", "Project 2", "actualCost")
                .doesNotContain("Project 3", "Project 4", "Project 5");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inaccessibleSessions")
    @DisplayName("should reject inaccessible sessions")
    void shouldRejectInaccessibleSessions(
            String scenario, LocalDate challengeDate, Long requesterId, String expectedErrorCode) {
        when(repository.findSession(SESSION_ID))
                .thenReturn(Optional.of(session(challengeDate)));

        BudgetGameException exception = catchThrowableOfType(
                BudgetGameException.class,
                () -> service.getSession(SESSION_ID, requesterId));

        assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode);
    }

    private static Stream<Arguments> inaccessibleSessions() {
        return Stream.of(
                Arguments.of("anonymous request", TODAY, null, "BUDGET_SESSION_ACCESS_DENIED"),
                Arguments.of("another user", TODAY, 2L, "BUDGET_SESSION_ACCESS_DENIED"),
                Arguments.of("expired challenge", TODAY.minusDays(1), USER_ID, "BUDGET_SESSION_EXPIRED"));
    }

    private static BudgetGameSessionRow session(LocalDate challengeDate) {
        return new BudgetGameSessionRow(
                SESSION_ID, BudgetGameSessionServiceTest.USER_ID, false, CHALLENGE_ID, challengeDate,
                BudgetGameSessionStatus.IN_PROGRESS, 2, 100, 0, 100, 20, 500, 2, NOW, null);
    }

    private List<BudgetChallengeQuestionRow> questions() {
        return IntStream.rangeClosed(1, 5).mapToObj(this::question).toList();
    }

    private BudgetChallengeQuestionRow question(int position) {
        var project = new BudgetPromptProjectDto(
                "Project " + position, "JP", "Japan", "ASIA", BudgetCategory.BRIDGE, 2020);
        var prompt = new BudgetQuestionPromptDto(project, null, null, null);
        return new BudgetChallengeQuestionRow(
                200L + position, position, BudgetQuestionType.HOW_MUCH,
                BudgetDifficulty.EASY, prompt, 0, null, null);
    }

    private BudgetGameAnswerRow answer() {
        return new BudgetGameAnswerRow(
                201L, 1, new BigDecimal("100000000"), null,
                new BigDecimal("100000000"), "USD", null,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"), 100, new BigDecimal("100.00"), 0,
                new BigDecimal("100.00"), 100,
                objectMapper.createObjectNode().put("actualCost", 100000000), NOW);
    }
}
