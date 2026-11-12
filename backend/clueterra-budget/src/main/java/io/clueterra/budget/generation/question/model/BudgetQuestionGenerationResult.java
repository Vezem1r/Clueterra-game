package io.clueterra.budget.generation.question.model;

import java.util.Map;

public record BudgetQuestionGenerationResult(
        int generatedCount,
        int createdCount,
        int retiredCount,
        Map<BudgetQuestionType, Long> byType) {

    public BudgetQuestionGenerationResult {
        byType = Map.copyOf(byType);
    }
}
