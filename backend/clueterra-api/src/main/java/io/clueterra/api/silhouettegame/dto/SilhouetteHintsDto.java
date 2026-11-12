package io.clueterra.api.silhouettegame.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record SilhouetteHintsDto(
        SilhouetteRegionHintDto region,
        SilhouetteLocationHintDto location,
        @JsonRawValue String capitals,
        @JsonRawValue String flagColors,
        SilhouetteLandmarkHintDto landmark) {
}
