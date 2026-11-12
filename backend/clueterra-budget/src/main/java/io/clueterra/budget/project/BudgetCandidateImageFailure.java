package io.clueterra.budget.project;

public record BudgetCandidateImageFailure(
        String wikidataId,
        String category,
        String name,
        String failureReason) {
}
