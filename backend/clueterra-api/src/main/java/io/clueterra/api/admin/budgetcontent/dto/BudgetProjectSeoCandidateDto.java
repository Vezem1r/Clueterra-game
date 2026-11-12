package io.clueterra.api.admin.budgetcontent.dto;

import java.math.BigDecimal;

public record BudgetProjectSeoCandidateDto(
        String wikidataId,
        String projectName,
        String countryName,
        String category,
        Integer projectYear,
        BigDecimal answerCostUsd,
        String sourceCurrency,
        String contentQualityStatus,
        String costEvidenceTier,
        int costConfidenceScore,
        boolean imageAvailable,
        boolean primaryCostAvailable,
        int factCount,
        BudgetProjectSeoStatus publicationStatus,
        String slug) {
}
