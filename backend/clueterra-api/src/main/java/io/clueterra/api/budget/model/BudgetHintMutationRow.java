package io.clueterra.api.budget.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record BudgetHintMutationRow(
        String outcome,
        Integer index,
        JsonNode payload,
        BigDecimal effectiveMultiplierPercent,
        Instant revealedAt) {
}
