package io.clueterra.budget.project;

public record BudgetContentEvidence(
        boolean unverifiedProjectMetadata,
        boolean unverifiedMetrics,
        boolean unverifiedMilestones) {
}
