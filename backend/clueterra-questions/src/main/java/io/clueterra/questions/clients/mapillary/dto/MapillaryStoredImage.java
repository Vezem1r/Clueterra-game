package io.clueterra.questions.clients.mapillary.dto;

public record MapillaryStoredImage(
        String imageUrl,
        String originalImageUrl,
        String sourcePageUrl,
        String authorName,
        String licenseLabel,
        String licenseUrl,
        String attributionText) {

    public MapillaryStoredImage(String imageUrl, String originalImageUrl) {
        this(imageUrl, originalImageUrl, null, null, null, null, null);
    }
}
