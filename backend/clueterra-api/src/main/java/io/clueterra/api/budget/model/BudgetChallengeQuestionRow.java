package io.clueterra.api.budget.model;

import io.clueterra.api.budget.dto.BudgetDifficulty;
import io.clueterra.api.budget.dto.BudgetQuestionPromptDto;

public record BudgetChallengeQuestionRow(
        long variantId,
        int position,
        BudgetQuestionType type,
        BudgetDifficulty difficulty,
        BudgetQuestionPromptDto promptPayload,
        int availableHintCount,
        BudgetQuestionImageRow primaryImage,
        BudgetQuestionImageRow comparisonImage) {
}
