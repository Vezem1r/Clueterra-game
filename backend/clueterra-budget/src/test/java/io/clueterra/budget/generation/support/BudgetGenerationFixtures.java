package io.clueterra.budget.generation.support;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.clueterra.budget.generation.question.model.BudgetQuestionCostFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionMetricFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionMilestoneFact;
import io.clueterra.budget.generation.question.model.BudgetSourceQuestion;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectDatePrecision;
import io.clueterra.budget.project.ProjectMetricType;
import io.clueterra.budget.project.ProjectMilestoneType;
import io.clueterra.budget.project.ProjectLifecycleStatus;

public final class BudgetGenerationFixtures {

    private BudgetGenerationFixtures() {
    }

    public static BudgetSourceQuestion source(
            long questionId,
            long projectId,
            MegaprojectCategory category,
            BigDecimal answerCostUsd) {
        return source(questionId, projectId, category, answerCostUsd, BudgetDifficulty.MEDIUM);
    }

    public static BudgetSourceQuestion source(
            long questionId,
            long projectId,
            MegaprojectCategory category,
            BigDecimal answerCostUsd,
            BudgetDifficulty difficulty) {
        return new BudgetSourceQuestion(
                questionId,
                projectId,
                "Q" + projectId,
                "Project " + projectId,
                "Project description",
                "Wikipedia contributors",
                "https://en.wikipedia.org/w/index.php?oldid=" + projectId,
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/",
                "US",
                "United States",
                "NORTH_AMERICA",
                category,
                2020,
                ProjectCostKind.REPORTED_COST,
                ProjectLifecycleStatus.COMPLETED,
                null,
                answerCostUsd,
                "Official cost source",
                "https://example.com/cost/" + projectId,
                "https://example.com/reference/" + projectId,
                difficulty);
    }

    public static BudgetQuestionCostFact cost(
            long projectId,
            ProjectCostKind kind,
            BigDecimal amountUsdLow,
            BigDecimal amountUsdHigh) {
        return new BudgetQuestionCostFact(
                projectId,
                kind,
                amountUsdLow,
                amountUsdHigh,
                "USD",
                amountUsdLow,
                amountUsdHigh,
                2018,
                BigDecimal.ONE,
                LocalDate.of(2018, 12, 31),
                "ANNUAL_AVERAGE",
                "NONE",
                "Verified source amount",
                "Whole project scope",
                "Official budget source",
                "https://example.com/budget/" + projectId,
                "https://example.com/budget-reference/" + projectId);
    }

    public static BudgetQuestionMetricFact metric(
            long projectId, ProjectMetricType type, BigDecimal value, String unit) {
        return new BudgetQuestionMetricFact(
                projectId,
                type,
                value,
                unit,
                "Official metric source",
                "https://example.com/metric/" + projectId,
                "https://example.com/metric-reference/" + projectId);
    }

    public static BudgetQuestionMilestoneFact milestone(
            long projectId, ProjectMilestoneType type, int year) {
        return milestone(projectId, type, null, year);
    }

    public static BudgetQuestionMilestoneFact milestone(
            long projectId, ProjectMilestoneType type, LocalDate date, int year) {
        return new BudgetQuestionMilestoneFact(
                projectId,
                type,
                date,
                year,
                date == null ? ProjectDatePrecision.YEAR : ProjectDatePrecision.DAY,
                "Official schedule source",
                "https://example.com/schedule/" + projectId,
                "https://example.com/schedule-reference/" + projectId);
    }
}
