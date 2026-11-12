package io.clueterra.api.budget.dto;

public record BudgetImageDto(
        String url,
        String altText,
        String authorName,
        String attributionText,
        String licenseLabel,
        String licenseUrl,
        String sourcePageUrl) {
}
