package io.clueterra.api.game.repository;

import java.time.Instant;

public record GuestProgressRow(
        int playCount,
        int budgetPlayCount,
        long totalXp,
        int currentLevel,
        int levelDivisor,
        int currentStreak,
        int bestStreak,
        int badgesEarned,
        Instant expiresAt) {
}
