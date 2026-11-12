package io.clueterra.api.game.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionCountryDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        int order,
        int totalHints,
        int hintsRevealed,
        int attemptsRemaining,
        boolean isGuessed,
        Integer xpEarned,
        List<HintDto> hints,
        CountryDto correctCountry) {
}
