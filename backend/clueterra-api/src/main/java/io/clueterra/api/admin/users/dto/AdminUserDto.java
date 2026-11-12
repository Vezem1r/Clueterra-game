package io.clueterra.api.admin.users.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.clueterra.api.rivergame.dto.RiverPlayerStatsDto;
import io.clueterra.api.flaggame.dto.FlagPlayerStatsDto;
import io.clueterra.api.quizgame.dto.QuizPlayerStatsDto;
import io.clueterra.api.silhouettegame.dto.SilhouettePlayerStatsDto;

public record AdminUserDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String email,
        String username,
        String role,
        boolean enabled,
        String createdAt,
        String displayName,
        String avatarUrl,
        int totalXp,
        int currentLevel,
        int challengesPlayed,
        int challengesWon,
        int currentStreak,
        int bestStreak,
        int streakFreezes,
        int budgetCompletedChallenges,
        int budgetAnsweredQuestions,
        double budgetFullXpRate,
        double budgetAverageAccuracy,
        int budgetCleanSweeps,
        int budgetProjectsExplored,
        int budgetBestHigherLowerStreak,
        BigDecimal budgetProjectsMasteredValueUsd,
        RiverPlayerStatsDto riverStats,
        SilhouettePlayerStatsDto silhouetteStats,
        FlagPlayerStatsDto flagStats,
        QuizPlayerStatsDto quizStats,
        boolean reportsMuted,
        boolean profileLocked,
        boolean isGuest) {
}
