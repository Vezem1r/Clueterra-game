package io.clueterra.budget.project;

public enum ProjectCostKind {
    REPORTED_COST,
    ORIGINAL_BUDGET,
    APPROVED_BUDGET,
    FINAL_COST,
    CURRENT_ESTIMATE,
    REVISED_BUDGET,
    EXPENDITURE_TO_DATE,
    FINANCING_AMOUNT,
    CONTRACT_VALUE;

    public boolean supportsHowMuch() {
        return this == REPORTED_COST || this == FINAL_COST || this == CURRENT_ESTIMATE;
    }

    public int answerPriority() {
        return switch (this) {
            case FINAL_COST -> 4;
            case REPORTED_COST -> 3;
            case CURRENT_ESTIMATE -> 2;
            default -> 0;
        };
    }
}
