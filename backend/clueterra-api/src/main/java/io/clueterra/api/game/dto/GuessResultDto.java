package io.clueterra.api.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuessResultDto(
        boolean correct,
        int attemptsRemaining,
        int hintsRevealed,
        HintDto nextHint,
        CountryDto correctCountry,
        int xpEarned,
        boolean countryComplete,
        boolean sessionComplete) {
}
