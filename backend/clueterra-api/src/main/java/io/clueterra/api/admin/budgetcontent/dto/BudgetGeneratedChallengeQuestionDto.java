package io.clueterra.api.admin.budgetcontent.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

public record BudgetGeneratedChallengeQuestionDto(
        int position,
        long questionId,
        String questionType,
        String difficulty,
        String projectName,
        String comparisonProjectName,
        boolean active,
        BigDecimal answerValue,
        String answerUnit,
        String answerChoice,
        JsonNode prompt,
        JsonNode reveal) {
}
