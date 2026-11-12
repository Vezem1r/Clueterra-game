package io.clueterra.api.admin.budgetcontent.repository;

import java.time.LocalDate;

public record BudgetChallengeDeletionResult(
        long challengeId,
        LocalDate challengeDate,
        boolean future,
        boolean deleted,
        boolean hasSessions) {
}
