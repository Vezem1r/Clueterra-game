package io.clueterra.api.projectpages.repository;

import java.math.BigDecimal;
import java.time.Instant;

public record ProjectPageCardRow(
        String wikidataId,
        String slug,
        String name,
        String countryName,
        String category,
        Integer projectYear,
        BigDecimal answerCostUsd,
        BigDecimal sourceAmount,
        String sourceCurrency,
        String metaDescription,
        String imageSourceSha1,
        String imageAltText,
        Instant updatedAt) {
}
