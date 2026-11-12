package io.clueterra.api.legal.dto;

public record PublicDataSourceItemResponse(
        String slug,
        String name,
        String summary,
        String notice,
        String sourceLabel,
        String sourceUrl,
        String licenseLabel,
        String licenseUrl,
        String note) {
}
