package io.clueterra.api.admin.budgetcontent.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BudgetProjectSeoPageRequest(
        @NotNull
        @Size(max = 120)
        String slug,

        @NotNull
        @Size(max = 240)
        String costQuestion,

        @NotNull
        @Size(max = 90)
        String seoTitle,

        @NotNull
        @Size(max = 220)
        String metaDescription,

        @NotNull
        @Size(max = 600)
        String keywords,

        @NotNull
        @Size(max = 4000)
        String intro,

        @NotNull
        @Size(max = 4000)
        String answerDetail,

        @NotNull
        @Size(max = 4000)
        String scopeText,

        @NotNull
        @Size(max = 4000)
        String contextText,

        @NotNull
        @Size(max = 4000)
        String methodologyText,

        @NotNull
        @Size(max = 240)
        String sourceName,

        @NotNull
        @Size(max = 2000)
        String sourceUrl,

        @NotNull
        @Size(max = 12)
        List<@Valid BudgetProjectSeoReferenceInput> references,

        @NotNull
        @Size(max = 12)
        List<@Valid BudgetProjectSeoFactInput> facts,

        @NotNull
        BudgetProjectSeoStatus publicationStatus) {
}
