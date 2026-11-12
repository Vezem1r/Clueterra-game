package io.clueterra.budget.generation.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.clueterra.budget.generation.challenge.model.BudgetChallengeCandidate;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;

class BudgetChallengeQuestionSelectorTest {

    private final BudgetChallengeQuestionSelector selector = new BudgetChallengeQuestionSelector();

    @Test
    @DisplayName("should select five deterministic questions without repeating projects")
    void shouldSelectFiveDeterministicQuestionsWithoutRepeatingProjects() {
        List<BudgetChallengeCandidate> candidates = List.of(
                candidate(1, BudgetQuestionType.HOW_MUCH, 1, null, MegaprojectCategory.BRIDGE),
                candidate(2, BudgetQuestionType.HIGHER_LOWER, 2, 20L, MegaprojectCategory.STADIUM),
                candidate(3, BudgetQuestionType.COST_PER_UNIT, 3, null, MegaprojectCategory.DAM),
                candidate(4, BudgetQuestionType.BUDGET_OVERRUN, 4, null, MegaprojectCategory.AIRPORT),
                candidate(5, BudgetQuestionType.HOW_LATE, 5, null, MegaprojectCategory.ENERGY),
                candidate(6, BudgetQuestionType.ORIGINAL_BUDGET, 6, null, MegaprojectCategory.SPACE));
        LocalDate date = LocalDate.of(2026, 9, 8);

        List<BudgetChallengeCandidate> first = selector.select(date, candidates, Set.of());
        List<BudgetChallengeCandidate> second = selector.select(date, candidates, Set.of());

        assertThat(first).hasSize(5).containsExactlyElementsOf(second);
        assertThat(first.stream().flatMap(candidate -> candidate.projectIds().stream()).toList())
                .doesNotHaveDuplicates();
        assertThat(first).extracting(BudgetChallengeCandidate::questionType)
                .contains(
                        BudgetQuestionType.HOW_MUCH,
                        BudgetQuestionType.HIGHER_LOWER,
                        BudgetQuestionType.COST_PER_UNIT,
                        BudgetQuestionType.HOW_LATE)
                .containsAnyOf(
                        BudgetQuestionType.BUDGET_OVERRUN,
                        BudgetQuestionType.ORIGINAL_BUDGET);
    }

    @Test
    @DisplayName("should avoid recently used projects when enough fresh questions exist")
    void shouldAvoidRecentlyUsedProjectsWhenEnoughFreshQuestionsExist() {
        List<BudgetChallengeCandidate> candidates = List.of(
                candidate(1, BudgetQuestionType.HOW_MUCH, 1, null, MegaprojectCategory.BRIDGE),
                candidate(2, BudgetQuestionType.HIGHER_LOWER, 2, 20L, MegaprojectCategory.STADIUM),
                candidate(3, BudgetQuestionType.COST_PER_UNIT, 3, null, MegaprojectCategory.DAM),
                candidate(4, BudgetQuestionType.BUDGET_OVERRUN, 4, null, MegaprojectCategory.AIRPORT),
                candidate(5, BudgetQuestionType.HOW_LATE, 5, null, MegaprojectCategory.ENERGY),
                candidate(11, BudgetQuestionType.HOW_MUCH, 11, null, MegaprojectCategory.BRIDGE),
                candidate(12, BudgetQuestionType.HIGHER_LOWER, 12, 21L, MegaprojectCategory.STADIUM),
                candidate(13, BudgetQuestionType.COST_PER_UNIT, 13, null, MegaprojectCategory.DAM),
                candidate(14, BudgetQuestionType.ORIGINAL_BUDGET, 14, null, MegaprojectCategory.AIRPORT),
                candidate(15, BudgetQuestionType.HOW_LATE, 15, null, MegaprojectCategory.ENERGY));

        List<BudgetChallengeCandidate> selected = selector.select(
                LocalDate.of(2026, 9, 9), candidates, Set.of(1L, 2L, 3L, 4L, 5L, 20L));

        assertThat(selected.stream().flatMap(candidate -> candidate.projectIds().stream()).toList())
                .doesNotContain(1L, 2L, 3L, 4L, 5L, 20L);
    }

    @Test
    @DisplayName("should fall back to recent projects rather than leave a challenge incomplete")
    void shouldFallBackToRecentProjectsRatherThanLeaveChallengeIncomplete() {
        List<BudgetChallengeCandidate> candidates = List.of(
                candidate(1, BudgetQuestionType.HOW_MUCH, 1, null, MegaprojectCategory.BRIDGE),
                candidate(2, BudgetQuestionType.HIGHER_LOWER, 2, 20L, MegaprojectCategory.STADIUM),
                candidate(3, BudgetQuestionType.COST_PER_UNIT, 3, null, MegaprojectCategory.DAM),
                candidate(4, BudgetQuestionType.BUDGET_OVERRUN, 4, null, MegaprojectCategory.AIRPORT),
                candidate(5, BudgetQuestionType.HOW_LATE, 5, null, MegaprojectCategory.ENERGY));

        List<BudgetChallengeCandidate> selected = selector.select(
                LocalDate.of(2026, 9, 10), candidates, Set.of(1L, 2L, 3L, 4L, 5L, 20L));

        assertThat(selected).hasSize(5);
    }

    @Test
    @DisplayName("should reject a challenge when five non-overlapping projects are unavailable")
    void shouldRejectChallengeWhenFiveNonOverlappingProjectsAreUnavailable() {
        List<BudgetChallengeCandidate> candidates = List.of(
                candidate(1, BudgetQuestionType.HOW_MUCH, 1, null, MegaprojectCategory.BRIDGE),
                candidate(2, BudgetQuestionType.HIGHER_LOWER, 2, 3L, MegaprojectCategory.STADIUM),
                candidate(3, BudgetQuestionType.COST_PER_UNIT, 3, null, MegaprojectCategory.DAM),
                candidate(4, BudgetQuestionType.BUDGET_OVERRUN, 4, null, MegaprojectCategory.AIRPORT),
                candidate(5, BudgetQuestionType.HOW_LATE, 1, null, MegaprojectCategory.ENERGY));

        assertThatThrownBy(() -> selector.select(
                        LocalDate.of(2026, 9, 10), candidates, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("five non-overlapping");
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
