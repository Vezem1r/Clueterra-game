package io.clueterra.budget.project;

public record BudgetEnrichmentBatchResult(int claimed, int succeeded, int failed) {

    public static BudgetEnrichmentBatchResult empty() {
        return new BudgetEnrichmentBatchResult(0, 0, 0);
    }
}
