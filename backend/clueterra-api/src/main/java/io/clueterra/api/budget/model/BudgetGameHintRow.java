package io.clueterra.api.budget.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record BudgetGameHintRow(
        long variantId,
        int position,
        int index,
        BigDecimal multiplierPercent,
        JsonNode payload,
        Instant revealedAt) {
}
