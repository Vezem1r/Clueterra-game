package io.clueterra.api.admin.budgetcontent.repository;

public record BudgetContentCursor(
        int qualityScore, int monthlyPageviews, String wikidataId, String category) {
}
