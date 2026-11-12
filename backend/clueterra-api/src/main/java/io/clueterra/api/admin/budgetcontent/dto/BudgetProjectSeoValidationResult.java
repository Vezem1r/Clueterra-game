package io.clueterra.api.admin.budgetcontent.dto;

import java.util.List;

public record BudgetProjectSeoValidationResult(
        boolean publishable,
        long errorCount,
        long warningCount,
        List<BudgetProjectSeoValidationIssue> issues) {
}
