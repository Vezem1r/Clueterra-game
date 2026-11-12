package io.clueterra.questions.ai.service.generation;

public record AiWikimediaAttribution(
        String sourcePageUrl,
        String authorName,
        String attributionText,
        String licenseLabel,
        String licenseUrl) {
}
