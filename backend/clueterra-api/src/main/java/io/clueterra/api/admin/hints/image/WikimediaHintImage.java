package io.clueterra.api.admin.hints.image;

public record WikimediaHintImage(
        String title,
        String sourceSha1,
        String thumbnailUrl,
        String originalUrl,
        String sourcePageUrl,
        String authorName,
        String attributionText,
        String licenseLabel,
        String licenseUrl,
        String mimeType,
        long sourceBytes,
        int width,
        int height) {
}
