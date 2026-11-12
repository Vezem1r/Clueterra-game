package io.clueterra.api.budget.model;

public record BudgetQuestionImageRow(
        String objectKey,
        String altText,
        String authorName,
        String attributionText,
        String licenseLabel,
        String licenseUrl,
        String sourcePageUrl) {
}
