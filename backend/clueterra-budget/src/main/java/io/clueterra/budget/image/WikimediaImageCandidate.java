package io.clueterra.budget.image;

public record WikimediaImageCandidate(
        long pageId,
        String sourceSha1,
        String title,
        String thumbnailUrl,
        String originalUrl,
        String sourcePageUrl,
        String authorName,
        String credit,
        String licenseLabel,
        String licenseUrl,
        String description,
        String mimeType,
        long sourceBytes,
        int width,
        int height) {
}
