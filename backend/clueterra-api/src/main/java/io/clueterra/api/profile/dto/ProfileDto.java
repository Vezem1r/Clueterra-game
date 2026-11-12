package io.clueterra.api.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileDto(
        String username,
        String email,
        String displayName,
        String avatarUrl,
        long totalXp,
        int level,
        int completedRuns,
        int countriesSolved,
        int perfectRuns,
        double solveRate,
        int currentStreak,
        int bestStreak,
        Integer leaderboardRank,
        String favoriteDifficulty,
        long xpForCurrentLevel,
        long xpForNextLevel,
        int levelDivisor,
        double streakBonusPercent,
        int freezesAvailable,
        Boolean profileLocked,
        Boolean socialDiscoverable,
        Boolean todayCompleted,
        String resetAtUtc,
        Boolean freezeUsedToday,
        Integer previousFreezeAtStreak,
        Integer maxFreezes,
        Integer nextFreezeAtStreak,
        Integer freezesUsedSinceLastCompletion,
        Boolean freezeNoticeAcknowledged,
        String difficultyRecommendation,
        BudgetProfileStatsDto budgetStats) {
}
