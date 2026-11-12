package io.clueterra.budget.operation;

import io.clueterra.budget.project.BudgetEnrichmentBatchResult;

public record BudgetStageProgress(int total, int processed, int succeeded, int failed) {

    public static BudgetStageProgress start(int total) {
        return new BudgetStageProgress(Math.max(0, total), 0, 0, 0);
    }

    public BudgetStageProgress advance(BudgetEnrichmentBatchResult result) {
        return new BudgetStageProgress(
                total,
                Math.min(total, processed + result.claimed()),
                succeeded + result.succeeded(),
                failed + result.failed());
    }

    public boolean reachedNewMilestone(BudgetStageProgress previous) {
        return milestonePercent() > previous.milestonePercent();
    }

    public int milestonePercent() {
        if (total == 0 || processed >= total) {
            return 100;
        }
        return ((processed * 100) / total / 10) * 10;
    }
}
