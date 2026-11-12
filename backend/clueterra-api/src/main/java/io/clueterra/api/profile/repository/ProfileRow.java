package io.clueterra.api.profile.repository;

import java.math.BigDecimal;

public record ProfileRow(
        String username,
        String email,
        String displayName,
        String avatarUrl,
        long totalXp,
        int currentLevel,
        int completedRuns,
        int countriesSolved,
        int perfectRuns,
        double solveRate,
        int currentStreak,
        int bestStreak,
        Integer leaderboardRank,
        String favoriteDifficulty,
        boolean profileLocked,
        int streakFreezes,
        int streakFreezesUsedSinceCompletion,
        int streakFreezesAcknowledgedSinceCompletion,
        boolean todayCompleted,
        boolean freezeUsedToday,
        boolean socialDiscoverable,
        int budgetCompletedChallenges,
        int budgetProjectsExplored,
        BigDecimal budgetProjectsMasteredValueUsd) {

    public static ProfileRow publicProfile(
            String username, String displayName, String avatarUrl,
            long totalXp, int currentLevel, int completedRuns,
            int countriesSolved, int perfectRuns, double solveRate,
            int currentStreak, int bestStreak, Integer leaderboardRank,
            String favoriteDifficulty, int streakFreezes,
            int budgetCompletedChallenges, int budgetProjectsExplored,
            BigDecimal budgetProjectsMasteredValueUsd) {
        return new ProfileRow(
                username, null, displayName, avatarUrl,
                totalXp, currentLevel, completedRuns, countriesSolved,
                perfectRuns, solveRate, currentStreak, bestStreak,
                leaderboardRank, favoriteDifficulty,
                false, streakFreezes, 0, 0, false, false, false,
                budgetCompletedChallenges, budgetProjectsExplored, budgetProjectsMasteredValueUsd);
    }
}
