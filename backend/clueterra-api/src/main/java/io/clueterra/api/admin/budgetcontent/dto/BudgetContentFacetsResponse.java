package io.clueterra.api.admin.budgetcontent.dto;

import java.util.List;

public record BudgetContentFacetsResponse(
        long total,
        List<BudgetFacetCountDto> statuses,
        List<BudgetFacetCountDto> categories,
        List<BudgetFacetCountDto> countries,
        List<BudgetFacetCountDto> difficulties,
        List<BudgetFacetCountDto> regions,
        List<BudgetFacetCountDto> issues,
        List<BudgetFacetCountDto> questionStatuses,
        List<BudgetFacetCountDto> challengeStatuses) {
}
