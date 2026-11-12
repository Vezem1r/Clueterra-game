package io.clueterra.api.admin.budgetcontent.repository;

import io.clueterra.api.admin.budgetcontent.dto.BudgetContentIssue;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentTab;

public record BudgetContentFilter(
        BudgetContentTab tab,
        String category,
        String countryCode,
        String difficulty,
        String search,
        Integer minimumQuality,
        BudgetContentIssue issue) {
}
