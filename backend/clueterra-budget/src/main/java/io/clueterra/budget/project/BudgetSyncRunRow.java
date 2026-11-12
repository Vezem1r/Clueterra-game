package io.clueterra.budget.project;

import java.time.Instant;

public record BudgetSyncRunRow(
    long id,
    String sourceCode,
    String status,
    Instant startedAt,
    Instant completedAt,
    int categoriesAttempted,
    int categoriesSucceeded,
    int recordsDiscovered,
    int recordsRetained,
    String errorMessage) {}
