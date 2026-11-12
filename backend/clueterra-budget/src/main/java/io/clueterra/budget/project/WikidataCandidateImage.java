package io.clueterra.budget.project;

public record WikidataCandidateImage(
    long pageId,
    String sourceSha1,
    String fileTitle,
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
    int height,
    int qualityScore,
    String objectKey) {}
