package io.clueterra.budget.generation.question.model;

import java.util.List;
import java.util.Optional;

import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectMetricType;
import io.clueterra.budget.project.ProjectMilestoneType;

public record BudgetQuestionGenerationContext(
        BudgetSourceQuestion source,
        List<BudgetQuestionCostFact> costs,
        List<BudgetQuestionMetricFact> metrics,
        List<BudgetQuestionMilestoneFact> milestones) {

    public BudgetQuestionGenerationContext {
        costs = List.copyOf(costs);
        metrics = List.copyOf(metrics);
        milestones = List.copyOf(milestones);
    }

    public Optional<BudgetQuestionCostFact> cost(ProjectCostKind kind) {
        return costs.stream().filter(cost -> cost.kind() == kind).findFirst();
    }

    public Optional<BudgetQuestionMetricFact> metric(ProjectMetricType type) {
        return metrics.stream().filter(metric -> metric.type() == type).findFirst();
    }

    public Optional<BudgetQuestionMilestoneFact> milestone(ProjectMilestoneType type) {
        return milestones.stream().filter(milestone -> milestone.type() == type).findFirst();
    }
}
