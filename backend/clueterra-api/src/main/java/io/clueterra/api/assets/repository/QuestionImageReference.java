package io.clueterra.api.assets.repository;

public record QuestionImageReference(
        String imageUrl,
        String originalImageUrl,
        String imageSourcePageUrl,
        String source) {
}
