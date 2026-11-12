package io.clueterra.budget.service.console;

public record BudgetOperationStatus(
        String id, String name, String status, long startedAt, Long completedAt, String error) {
}
