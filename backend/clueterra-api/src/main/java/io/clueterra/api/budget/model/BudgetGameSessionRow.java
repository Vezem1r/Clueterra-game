package io.clueterra.api.budget.model;

import java.time.Instant;
import java.time.LocalDate;

public record BudgetGameSessionRow(
        long id,
        long userId,
        boolean guest,
        long challengeId,
        LocalDate challengeDate,
        BudgetGameSessionStatus status,
        int currentPosition,
        int totalXpEarned,
        int streakBonusXp,
        int baseXpPerQuestion,
        int fullXpThresholdPercent,
        long xpBefore,
        int levelBefore,
        Instant startedAt,
        Instant completedAt) {
}
