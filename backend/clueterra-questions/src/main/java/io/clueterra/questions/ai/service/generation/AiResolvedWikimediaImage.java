package io.clueterra.questions.ai.service.generation;

public record AiResolvedWikimediaImage(
        String title,
        String imageUrl,
        String sourcePageUrl,
        String licenseLabel,
        String licenseUrl,
        String authorName,
        String attributionText) {
}
