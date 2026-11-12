package io.clueterra.api.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameResultsCommunityStatsDto(
        boolean enoughData,
        int avgXpPerCompletedRun,
        double accuracyRate,
        long avgActiveTimePerSessionMs,
        double avgAttemptsPerCountry,
        double avgHintsPerCountry,
        HardestCountryInsightDto hardestCountry) {
}
