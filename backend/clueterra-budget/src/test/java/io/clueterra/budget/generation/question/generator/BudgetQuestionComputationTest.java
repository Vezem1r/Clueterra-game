package io.clueterra.budget.generation.question.generator;

import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.cost;
import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.milestone;
import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.source;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectMilestoneType;

class BudgetQuestionComputationTest {

    @ParameterizedTest(name = "final={0}, original range={1}..{2} -> {3}%")
    @CsvSource({
            "150, 100, 100, 50.000000",
            "110, 100, 100, 10.000000",
            "90, 100, 100, -10.000000",
            "200, 80, 120, 100.000000",
            "200, 150, 150, 33.333333"
    })
    @DisplayName("should calculate budget overrun from the original-budget midpoint")
    void shouldCalculateBudgetOverrunFromOriginalBudgetMidpoint(
            String finalCost, String originalLow, String originalHigh, String expectedPercent) {
        var source = source(1, 10, MegaprojectCategory.BRIDGE, new BigDecimal(finalCost));
        var original = cost(
                source.projectId(),
                ProjectCostKind.ORIGINAL_BUDGET,
                new BigDecimal(originalLow),
                new BigDecimal(originalHigh));
        var context = new BudgetQuestionGenerationContext(
                source, List.of(original), List.of(), List.of());

        assertThat(BudgetQuestionComputation.budgetOverrunPercent(context))
                .contains(new BigDecimal(expectedPercent));
    }

    @Test
    @DisplayName("should calculate delay in full calendar months when exact dates are available")
    void shouldCalculateDelayInFullCalendarMonths() {
        var source = source(1, 10, MegaprojectCategory.BRIDGE, BigDecimal.TEN);
        var planned = milestone(
                source.projectId(),
                ProjectMilestoneType.PLANNED_COMPLETION,
                LocalDate.of(2018, 6, 15),
                2018);
        var actual = milestone(
                source.projectId(),
                ProjectMilestoneType.ACTUAL_COMPLETION,
                LocalDate.of(2020, 2, 1),
                2020);
        var context = new BudgetQuestionGenerationContext(
                source, List.of(), List.of(), List.of(planned, actual));

        assertThat(BudgetQuestionComputation.delay(context)).get()
                .extracting(delay -> delay.months())
                .isEqualTo(20L);
    }

    @ParameterizedTest(name = "actual milestone={0}")
    @EnumSource(
            value = ProjectMilestoneType.class,
            names = {"ACTUAL_COMPLETION", "OPENING", "CURRENT_COMPLETION_ESTIMATE"})
    @DisplayName("should use supported actual-completion milestone fallbacks")
    void shouldUseSupportedActualCompletionMilestoneFallbacks(ProjectMilestoneType actualType) {
        var source = source(1, 10, MegaprojectCategory.BRIDGE, BigDecimal.TEN);
        var planned = milestone(source.projectId(), ProjectMilestoneType.PLANNED_COMPLETION, 2018);
        var actual = milestone(source.projectId(), actualType, 2020);
        var context = new BudgetQuestionGenerationContext(
                source, List.of(), List.of(), List.of(planned, actual));

        assertThat(BudgetQuestionComputation.delay(context)).get()
                .extracting(delay -> delay.months())
                .isEqualTo(24L);
    }

    @ParameterizedTest(name = "planned={0}, actual={1}")
    @CsvSource({"2020, 2020", "2020, 2019"})
    @DisplayName("should not expose a delay for on-time or early projects")
    void shouldNotExposeDelayForOnTimeOrEarlyProjects(int plannedYear, int actualYear) {
        var source = source(1, 10, MegaprojectCategory.BRIDGE, BigDecimal.TEN);
        var planned = milestone(
                source.projectId(), ProjectMilestoneType.PLANNED_COMPLETION, plannedYear);
        var actual = milestone(
                source.projectId(), ProjectMilestoneType.ACTUAL_COMPLETION, actualYear);
        var context = new BudgetQuestionGenerationContext(
                source, List.of(), List.of(), List.of(planned, actual));

        assertThat(BudgetQuestionComputation.delay(context)).isEmpty();
    }
}
