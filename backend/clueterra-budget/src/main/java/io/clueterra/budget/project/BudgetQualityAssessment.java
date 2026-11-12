package io.clueterra.budget.project;

import java.util.List;

public record BudgetQualityAssessment(
        ContentQualityStatus status,
        CostEvidenceTier evidenceTier,
        int confidenceScore,
        List<BudgetQualityFlag> flags) {

    public BudgetQualityAssessment {
        flags = List.copyOf(flags);
    }
}
