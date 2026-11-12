package io.clueterra.api.admin.budgetcontent.dto;

import java.time.Instant;
import java.util.List;

public record BudgetProjectSeoPageDto(
        String wikidataId,
        String projectName,
        String countryName,
        String category,
        boolean projectAvailable,
        boolean imageAvailable,
        String slug,
        String costQuestion,
        String seoTitle,
        String metaDescription,
        String keywords,
        String intro,
        String answerDetail,
        String scopeText,
        String contextText,
        String methodologyText,
        String sourceName,
        String sourceUrl,
        List<BudgetProjectSeoReferenceInput> references,
        List<BudgetProjectSeoFactInput> facts,
        BudgetProjectSeoStatus publicationStatus,
        Instant publishedAt,
        String updatedBy,
        Instant updatedAt) {
}
