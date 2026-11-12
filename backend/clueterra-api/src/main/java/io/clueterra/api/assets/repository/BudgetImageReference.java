package io.clueterra.api.assets.repository;

public record BudgetImageReference(
        String objectKey, String fallbackImageUrl, String originalImageUrl) {
}
