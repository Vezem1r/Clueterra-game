package io.clueterra.budget.project;

public record BudgetCandidateStoredImage(
        long pageId,
        String sourceSha1,
        String fileTitle,
        String objectKey,
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
        int qualityScore) {
}
