package io.clueterra.budget.generation.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.budget.config.BudgetGenerationProperties;
import io.clueterra.budget.generation.challenge.model.BudgetChallengeCandidate;
import io.clueterra.budget.generation.challenge.model.BudgetChallengeGenerationSummary;
import io.clueterra.budget.generation.challenge.model.BudgetChallengeGenerationTrigger;
import io.clueterra.budget.generation.challenge.model.BudgetChallengePublication;
import io.clueterra.budget.generation.challenge.repository.BudgetDailyChallengeRepository;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;
import io.clueterra.budget.generation.question.service.BudgetQuestionGenerationService;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;

@ExtendWith(MockitoExtension.class)
class BudgetDailyChallengeGenerationServiceTest {

    @Mock
    private BudgetDailyChallengeRepository repository;

    @Mock
    private BudgetChallengeQuestionSelector selector;

    @Mock
    private BudgetQuestionGenerationService questionGenerationService;

    private BudgetDailyChallengeGenerationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC);
        service = service(clock);
        when(repository.findCandidates()).thenReturn(List.of());
    }

    private BudgetDailyChallengeGenerationService service(Clock clock) {
        BudgetGenerationProperties properties =
                new BudgetGenerationProperties(14, true, "0 30 4 * * *");
        return new BudgetDailyChallengeGenerationService(
                repository, selector, questionGenerationService, properties, clock);
    }

    @Test
    @DisplayName("should generate from today through seven days ahead and skip occupied dates")
    void shouldGenerateNowIncludingTodayAndSevenDaysAhead() {
        LocalDate today = LocalDate.of(2026, 9, 8);
        for (int offset = 0; offset <= 7; offset++) {
            when(repository.existsByDate(today.plusDays(offset))).thenReturn(true);
        }

        BudgetChallengeGenerationSummary result = service.generateNow();

        assertThat(result.fromDate()).isEqualTo(today);
        assertThat(result.toDate()).isEqualTo(today.plusDays(7));
        assertThat(result.requestedDates()).isEqualTo(8);
        assertThat(result.createdChallenges()).isZero();
        assertThat(result.skippedChallenges()).isEqualTo(8);
        verify(questionGenerationService).generate();
    }

    @Test
    @DisplayName("should maintain a scheduled eight-day rolling window")
    void shouldMaintainScheduledRollingWindow() {
        LocalDate today = LocalDate.of(2026, 9, 8);
        for (int offset = 0; offset <= 7; offset++) {
            when(repository.existsByDate(today.plusDays(offset))).thenReturn(true);
        }

        BudgetChallengeGenerationSummary result = service.generateRollingWindow();

        assertThat(result.fromDate()).isEqualTo(today);
        assertThat(result.toDate()).isEqualTo(today.plusDays(7));
        assertThat(result.requestedDates()).isEqualTo(8);
        assertThat(result.skippedChallenges()).isEqualTo(8);
        verify(questionGenerationService).generate();
    }

    @Test
    @DisplayName("should create an open date from selected questions and apply recent-project exclusion")
    void shouldCreateOpenDateFromSelectedQuestionsWithRecentProjectExclusion() {
        LocalDate today = LocalDate.of(2026, 9, 8);
        List<BudgetChallengeCandidate> candidates = candidates();
        Set<Long> recentProjects = Set.of(99L);
        when(repository.existsByDate(today)).thenReturn(false);
        for (int offset = 1; offset <= 7; offset++) {
            when(repository.existsByDate(today.plusDays(offset))).thenReturn(true);
        }
        when(repository.findCandidates()).thenReturn(candidates);
        when(repository.findUsedProjectIds(today.minusDays(14), today.minusDays(1)))
                .thenReturn(recentProjects);
        when(selector.select(today, candidates, recentProjects)).thenReturn(candidates);
        when(repository.create(
                        today,
                        BudgetChallengeGenerationTrigger.MANUAL,
                        List.of(1L, 2L, 3L, 4L, 5L)))
                .thenReturn(new BudgetChallengePublication(10L, today, true));

        BudgetChallengeGenerationSummary result = service.generateNow();

        assertThat(result.createdDates()).containsExactly(today);
        assertThat(result.createdChallenges()).isEqualTo(1);
        assertThat(result.skippedChallenges()).isEqualTo(7);
        var ordered = inOrder(questionGenerationService, repository, selector);
        ordered.verify(questionGenerationService).generate();
        ordered.verify(repository).findCandidates();
        ordered.verify(selector).select(today, candidates, recentProjects);
        ordered.verify(repository).create(
                today,
                BudgetChallengeGenerationTrigger.MANUAL,
                List.of(1L, 2L, 3L, 4L, 5L));
    }

    private List<BudgetChallengeCandidate> candidates() {
        return List.of(
                candidate(1, BudgetQuestionType.HOW_MUCH, 1, null, MegaprojectCategory.BRIDGE),
                candidate(2, BudgetQuestionType.HIGHER_LOWER, 2, 20L, MegaprojectCategory.STADIUM),
                candidate(3, BudgetQuestionType.COST_PER_UNIT, 3, null, MegaprojectCategory.DAM),
                candidate(4, BudgetQuestionType.BUDGET_OVERRUN, 4, null, MegaprojectCategory.AIRPORT),
                candidate(5, BudgetQuestionType.HOW_LATE, 5, null, MegaprojectCategory.ENERGY));
    }

    private BudgetChallengeCandidate candidate(
            long variantId,
            BudgetQuestionType type,
            long sourceProjectId,
            Long comparisonProjectId,
            MegaprojectCategory category) {
        return new BudgetChallengeCandidate(
                variantId,
                type,
                BudgetDifficulty.MEDIUM,
                sourceProjectId,
                comparisonProjectId,
                category);
    }
}
