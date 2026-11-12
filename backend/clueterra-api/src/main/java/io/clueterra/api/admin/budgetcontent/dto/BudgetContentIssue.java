package io.clueterra.api.admin.budgetcontent.dto;

public enum BudgetContentIssue {
    DUPLICATE_NAME,
    DUPLICATE_IMAGE,
    SUSPICIOUS_NAME,
    MISSING_DESCRIPTION,
    LOW_QUALITY,
    COST_OUTLIER,
    WEAK_COST_EVIDENCE,
    COST_REVIEW_REQUIRED
}
