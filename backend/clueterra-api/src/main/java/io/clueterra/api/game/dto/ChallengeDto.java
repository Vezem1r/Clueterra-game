package io.clueterra.api.game.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record ChallengeDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        LocalDate date,
        String level,
        String difficulty,
        int totalCountries,
        int totalHints,
        boolean available,
        int continentCount,
        int dataSourceCount,
        int categoryCount,
        Integer avgXp) {
}
