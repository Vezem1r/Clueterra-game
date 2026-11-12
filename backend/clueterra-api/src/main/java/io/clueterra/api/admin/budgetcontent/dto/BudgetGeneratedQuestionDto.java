package io.clueterra.api.admin.budgetcontent.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record BudgetGeneratedQuestionDto(
        long id,
        String questionType,
        String generationKey,
        String difficulty,
        boolean active,
        String projectName,
        String countryName,
        String category,
        String comparisonProjectName,
        BigDecimal answerValue,
        String answerUnit,
        String answerChoice,
        JsonNode prompt,
        JsonNode reveal,
        Instant generatedAt,
        Instant retiredAt) {
}
