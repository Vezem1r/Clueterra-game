package io.clueterra.api.flaggame.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record FlagHintsDto(
        FlagRegionHintDto region,
        @JsonRawValue String initials,
        @JsonRawValue String capitals) {
}
