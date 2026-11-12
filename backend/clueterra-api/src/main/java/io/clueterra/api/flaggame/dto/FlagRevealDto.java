package io.clueterra.api.flaggame.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record FlagRevealDto(
        String countryCode,
        String region,
        String subregion,
        @JsonRawValue String names,
        @JsonRawValue String capitals) {
}
