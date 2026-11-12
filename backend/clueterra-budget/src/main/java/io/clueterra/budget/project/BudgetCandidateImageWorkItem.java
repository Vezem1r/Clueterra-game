package io.clueterra.budget.project;

public record BudgetCandidateImageWorkItem(
        String wikidataId,
        MegaprojectCategory category,
        String imageFileTitle,
        String authorNameOverride,
        String creditOverride,
        int attemptCount) {
}
