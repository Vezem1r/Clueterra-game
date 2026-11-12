package io.clueterra.api.admin.budgetcontent.dto;

public record BudgetContentImageDto(
        String url,
        String originalUrl,
        String sourcePageUrl,
        String fileTitle,
        String authorName,
        String credit,
        String licenseLabel,
        String licenseUrl,
        String description,
        String mimeType,
        Long sourceBytes,
        Integer width,
        Integer height,
        Integer qualityScore) {
}
