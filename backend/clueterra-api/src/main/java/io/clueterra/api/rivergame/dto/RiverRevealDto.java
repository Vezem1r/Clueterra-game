package io.clueterra.api.rivergame.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record RiverRevealDto(
        String name,
        String countryCode,
        double lengthKm,
        String mapImageUrl,
        @JsonRawValue String facts,
        RiverPhotoDto photo,
        String wikipediaUrl,
        String wikidataUrl) {
}
