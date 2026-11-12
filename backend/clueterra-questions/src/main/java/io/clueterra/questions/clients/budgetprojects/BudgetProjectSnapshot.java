package io.clueterra.questions.clients.budgetprojects;

import java.math.BigDecimal;

public record BudgetProjectSnapshot(
        String wikidataId,
        String name,
        String countryCode,
        String countryName,
        String category,
        String difficulty,
        String lifecycleStatus,
        Integer openingYear,
        BigDecimal costUsd,
        String costKind,
        String metricType,
        BigDecimal metricValue,
        String metricUnit,
        boolean eligible,
        String imageObjectKey,
        String imageSourceSha1,
        String originalImageUrl,
        String imageSourcePageUrl,
        String imageAuthorName,
        String imageAttributionText,
        String imageLicenseLabel,
        String imageLicenseUrl
) {
}
