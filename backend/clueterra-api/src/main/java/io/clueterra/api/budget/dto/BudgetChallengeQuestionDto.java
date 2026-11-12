package io.clueterra.api.budget.dto;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.clueterra.api.budget.model.BudgetQuestionType;

public record BudgetChallengeQuestionDto(
        @JsonSerialize(using = ToStringSerializer.class)
        long variantId,
        int position,
        BudgetQuestionType type,
        BudgetDifficulty difficulty,
        BudgetQuestionPromptDto prompt,
        int availableHintCount,
        List<BudgetHintDto> revealedHints,
        BudgetImageDto primaryImage,
        BudgetImageDto comparisonImage) {
}
