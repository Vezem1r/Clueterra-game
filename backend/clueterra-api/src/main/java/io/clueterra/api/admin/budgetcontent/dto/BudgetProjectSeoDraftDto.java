package io.clueterra.api.admin.budgetcontent.dto;

import java.util.List;

public record BudgetProjectSeoDraftDto(
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
        List<BudgetProjectSeoFactInput> facts) {
}
