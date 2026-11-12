package io.clueterra.api.game.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultCountryDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        int order,
        int totalHints,
        int hintsSeen,
        boolean guessed,
        int xpEarned,
        CountryDto correctCountry,
        List<HintDto> hints,
        List<GuessAttemptDto> attempts) {
}
