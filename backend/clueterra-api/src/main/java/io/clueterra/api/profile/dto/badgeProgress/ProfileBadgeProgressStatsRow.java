package io.clueterra.api.profile.dto.badgeProgress;

public record ProfileBadgeProgressStatsRow(
        long totalXp,
        int currentLevel,
        int challengesPlayed,
        int challengesWon,
        int currentStreak,
        int bestStreak,
        int perfectRuns,
        long budgetCompletedChallenges,
        long budgetExactGuesses,
        long budgetCleanSweeps,
        long budgetUniqueProjects,
        long budgetBestHigherLowerStreak,
        long budgetHigherLowerCorrect,
        long budgetCostPerUnitCorrect,
        long budgetDistinctCountries,
        long budgetMasteredValueUsd,
        long budgetSevereMisses,
        long riverAttempted,
        long riverSolved,
        long silhouetteSolved,
        long silhouetteTotal,
        long flagSolved,
        long flagTotal,
        long quizExplored,
        long quizTotal) {
}
