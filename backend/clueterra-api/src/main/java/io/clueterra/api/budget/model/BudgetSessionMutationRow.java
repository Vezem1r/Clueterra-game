package io.clueterra.api.budget.model;

public record BudgetSessionMutationRow(
        String outcome, Long sessionId, Long variantId, Integer position) {
}
