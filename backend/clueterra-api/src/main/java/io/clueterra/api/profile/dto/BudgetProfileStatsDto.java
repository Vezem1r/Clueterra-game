package io.clueterra.api.profile.dto;

import java.math.BigDecimal;

public record BudgetProfileStatsDto(
        int completedChallenges,
        int projectsExplored,
        BigDecimal projectsMasteredValueUsd) {
}
