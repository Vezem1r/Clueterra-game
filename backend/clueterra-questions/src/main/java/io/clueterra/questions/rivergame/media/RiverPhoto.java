package io.clueterra.questions.rivergame.media;

public record RiverPhoto(
        String objectKey,
        String originalUrl,
        String sourcePageUrl,
        String authorName,
        String attributionText,
        String licenseLabel,
        String licenseUrl,
        String sourceSha1,
        String mimeType,
        Integer width,
        Integer height,
        Long byteSize,
        String contentSha256) {
}
