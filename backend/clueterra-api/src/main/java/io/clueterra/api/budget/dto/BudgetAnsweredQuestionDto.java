package io.clueterra.api.budget.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import io.clueterra.api.budget.model.BudgetAnswerChoice;

public record BudgetAnsweredQuestionDto(
        BudgetChallengeQuestionDto question,
        BigDecimal guessedValue,
        BudgetAnswerChoice guessedChoice,
        BigDecimal correctValue,
        String correctUnit,
        BudgetAnswerChoice correctChoice,
        BigDecimal accuracyPercent,
        BigDecimal rawXpPercent,
        int rawXpEarned,
        BigDecimal hintMultiplierPercent,
        int hintsUsed,
        BigDecimal xpPercent,
        int xpEarned,
        JsonNode reveal,
        Instant answeredAt) {
}
