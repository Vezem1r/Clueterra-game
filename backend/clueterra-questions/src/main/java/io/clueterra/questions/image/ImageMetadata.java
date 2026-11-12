package io.clueterra.questions.image;

public record ImageMetadata(
        String originalImageUrl,
        String sourcePageUrl,
        String authorName,
        String attributionText,
        String licenseLabel,
        String licenseUrl,
        String downloadUrl,
        String sourceSha1,
        String mimeType,
        Integer width,
        Integer height) {

    public ImageMetadata(
            String originalImageUrl,
            String sourcePageUrl,
            String authorName,
            String attributionText,
            String licenseLabel,
            String licenseUrl) {
        this(originalImageUrl, sourcePageUrl, authorName, attributionText, licenseLabel,
                licenseUrl, originalImageUrl, null, null, null, null);
    }
}
