package io.clueterra.budget.generation.question.generator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import io.clueterra.budget.generation.question.model.BudgetQuestionCostFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionDelay;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetQuestionMilestoneFact;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectMilestoneType;

public final class BudgetQuestionComputation {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private BudgetQuestionComputation() {
    }

    public static Optional<BigDecimal> budgetOverrunPercent(BudgetQuestionGenerationContext context) {
        return context.cost(ProjectCostKind.ORIGINAL_BUDGET)
                .map(BudgetQuestionCostFact::midpointUsd)
                .filter(original -> original.signum() > 0)
                .map(original -> context.source().answerCostUsd()
                        .subtract(original)
                        .multiply(ONE_HUNDRED)
                        .divide(original, 6, RoundingMode.HALF_UP));
    }

    public static Optional<BudgetQuestionDelay> delay(BudgetQuestionGenerationContext context) {
        Optional<BudgetQuestionMilestoneFact> planned =
                context.milestone(ProjectMilestoneType.PLANNED_COMPLETION);
        Optional<BudgetQuestionMilestoneFact> actual = actualMilestone(context);
        if (planned.isEmpty() || actual.isEmpty()) {
            return Optional.empty();
        }

        YearMonth plannedMonth = month(planned.orElseThrow());
        YearMonth actualMonth = month(actual.orElseThrow());
        long months = ChronoUnit.MONTHS.between(plannedMonth, actualMonth);
        return months > 0
                ? Optional.of(new BudgetQuestionDelay(planned.orElseThrow(), actual.orElseThrow(), months))
                : Optional.empty();
    }

    public static BudgetDifficulty promote(BudgetDifficulty difficulty, int levels) {
        int ordinal = Math.min(BudgetDifficulty.HARD.ordinal(), difficulty.ordinal() + levels);
        return BudgetDifficulty.values()[ordinal];
    }

    public static BudgetDifficulty max(BudgetDifficulty first, BudgetDifficulty second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private static Optional<BudgetQuestionMilestoneFact> actualMilestone(
            BudgetQuestionGenerationContext context) {
        return context.milestone(ProjectMilestoneType.ACTUAL_COMPLETION)
                .or(() -> context.milestone(ProjectMilestoneType.OPENING))
                .or(() -> context.milestone(ProjectMilestoneType.CURRENT_COMPLETION_ESTIMATE));
    }

    private static YearMonth month(BudgetQuestionMilestoneFact milestone) {
        return milestone.date() != null
                ? YearMonth.from(milestone.date())
                : YearMonth.of(milestone.year(), 1);
    }
}
