package io.clueterra.api.silhouettegame.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record SilhouettePhotoDto(
        String url,
        String authorName,
        String licenseLabel,
        String licenseUrl,
        String sourcePageUrl,
        @JsonRawValue String landmarkNames) {
}
