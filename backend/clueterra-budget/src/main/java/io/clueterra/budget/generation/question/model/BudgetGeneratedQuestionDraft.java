package io.clueterra.budget.generation.question.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

import io.clueterra.budget.project.BudgetDifficulty;

public record BudgetGeneratedQuestionDraft(
        long sourceQuestionId,
        Long comparisonQuestionId,
        BudgetQuestionType questionType,
        String generationKey,
        BigDecimal answerValue,
        String answerUnit,
        String answerChoice,
        BudgetQuestionPromptPayload promptPayload,
        JsonNode revealPayload,
        BudgetDifficulty difficulty) {

    public static String unaryKey(BudgetQuestionType type, long questionId) {
        return "%s:%d".formatted(type.name(), questionId);
    }

    public static String comparisonKey(long sourceQuestionId, long comparisonQuestionId) {
        return "%s:%d:%d"
                .formatted(
                        BudgetQuestionType.HIGHER_LOWER.name(),
                        sourceQuestionId,
                        comparisonQuestionId);
    }
}
