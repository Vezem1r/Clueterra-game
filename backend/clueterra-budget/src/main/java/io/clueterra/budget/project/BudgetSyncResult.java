package io.clueterra.budget.project;

import java.time.Instant;
import java.util.List;

public record BudgetSyncResult(
        long runId,
        String source,
        String status,
        int categoriesAttempted,
        int categoriesSucceeded,
        int categoriesFailed,
        int recordsDiscovered,
        int recordsRetained,
        List<String> failures,
        Instant completedAt) {
}
