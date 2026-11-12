package io.clueterra.api.profile.dto;

import java.time.Instant;

public record ProfileDifficultyStatsDto(
        String level,
        int completedRuns,
        int countriesPlayed,
        int countriesSolved,
        double solveRate,
        Instant lastCompletedAt) {
}
