package io.clueterra.api.admin.budgetcontent.dto;

public record BudgetProjectSeoValidationIssue(
        String code,
        BudgetProjectSeoValidationSeverity severity,
        String field,
        String message) {
}
