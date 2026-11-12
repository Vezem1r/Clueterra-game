package io.clueterra.api.game.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.clueterra.api.common.enums.SessionStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameSessionSummaryDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String level,
        SessionStatus status,
        int currentCountryIndex,
        int totalCountries,
        int guessedCountries,
        int totalHintsRevealed,
        int totalAttemptsUsed,
        Instant startedAt,
        long activeTimeMs,
        Instant completedAt,
        int totalXpEarned) {
}
