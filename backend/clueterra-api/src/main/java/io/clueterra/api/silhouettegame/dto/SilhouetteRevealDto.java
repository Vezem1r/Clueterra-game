package io.clueterra.api.silhouettegame.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record SilhouetteRevealDto(
        String countryCode,
        String subregion,
        @JsonRawValue String names,
        @JsonRawValue String facts,
        @JsonRawValue String flagColors,
        List<Double> center,
        @JsonRawValue String bounds,
        String shapePath,
        SilhouettePhotoDto photo) {
}
