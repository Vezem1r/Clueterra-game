package io.clueterra.api.admin.budgetcontent.repository;

import java.math.BigDecimal;

public record BudgetProjectSeoEditorialContext(
        String wikidataId,
        String projectName,
        String description,
        String countryName,
        String category,
        Integer projectYear,
        BigDecimal answerCostUsd,
        BigDecimal sourceAmount,
        String sourceCurrency,
        Integer costYear,
        String normalizationNote,
        String costSourceName,
        String costSourceUrl,
        String costEvidenceTier,
        int costConfidenceScore,
        boolean hasQualityFlags,
        boolean imageAvailable,
        boolean imageMetadataComplete,
        boolean primaryCostAvailable,
        String costVerificationStatus,
        String costEvidenceText,
        String costScopeNote,
        int metricCount,
        int milestoneCount) {
}
