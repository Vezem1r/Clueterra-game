package io.clueterra.api.budget.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record BudgetHintDto(
        int index,
        BigDecimal multiplierPercent,
        JsonNode payload,
        Instant revealedAt) {
}
