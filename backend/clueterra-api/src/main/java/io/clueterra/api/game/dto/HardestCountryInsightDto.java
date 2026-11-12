package io.clueterra.api.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HardestCountryInsightDto(
        CountryDto country,
        double successRate,
        double avgAttemptsNeeded,
        double avgHintsNeeded,
        long avgActiveTimeMs,
        CountryDto mostConfusedWith) {
}
