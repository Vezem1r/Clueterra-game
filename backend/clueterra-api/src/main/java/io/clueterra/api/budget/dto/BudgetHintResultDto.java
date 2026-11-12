package io.clueterra.api.budget.dto;

import java.math.BigDecimal;

public record BudgetHintResultDto(
        boolean replayed,
        BudgetHintDto hint,
        BigDecimal effectiveMultiplierPercent,
        BudgetGameSessionDto session) {
}
