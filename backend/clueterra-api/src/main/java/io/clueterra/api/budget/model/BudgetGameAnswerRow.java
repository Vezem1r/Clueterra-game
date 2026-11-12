package io.clueterra.api.budget.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record BudgetGameAnswerRow(
        long variantId,
        int position,
        BigDecimal guessedValue,
        BudgetAnswerChoice guessedChoice,
        BigDecimal answerValue,
        String answerUnit,
        BudgetAnswerChoice answerChoice,
        BigDecimal accuracyPercent,
        BigDecimal rawXpPercent,
        int rawXpEarned,
        BigDecimal hintMultiplierPercent,
        int hintsUsed,
        BigDecimal xpPercent,
        int xpEarned,
        JsonNode revealPayload,
        Instant answeredAt) {
}
