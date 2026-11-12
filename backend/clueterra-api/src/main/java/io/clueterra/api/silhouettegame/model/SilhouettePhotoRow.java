package io.clueterra.api.silhouettegame.model;

public record SilhouettePhotoRow(
        String objectKey,
        String authorName,
        String licenseLabel,
        String licenseUrl,
        String sourcePageUrl,
        String landmarkNames) {
}
