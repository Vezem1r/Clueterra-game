package io.clueterra.api.game.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuessAttemptDto(
        int attemptNumber,
        CountryDto guessedCountry,
        boolean correct,
        int hintsSeen,
        Instant createdAt) {
}
