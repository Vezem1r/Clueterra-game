package io.clueterra.api.game.repository;

public record AwardResult(
        int newStreak,
        int newBestStreak,
        double streakBonusPercent,
        int streakBonusXp,
        int totalXpAwarded,
        int freezesEarned,
        int newLevel,
        int newFreezes) {
}
