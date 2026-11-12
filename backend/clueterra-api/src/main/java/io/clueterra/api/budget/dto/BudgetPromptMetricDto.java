package io.clueterra.api.budget.dto;

import java.math.BigDecimal;
import java.util.Objects;

public record BudgetPromptMetricDto(BudgetMetricType type, BigDecimal value, String unit) {

    public BudgetPromptMetricDto {
        Objects.requireNonNull(type, "Prompt metric type is required");
        Objects.requireNonNull(value, "Prompt metric value is required");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Prompt metric value must be positive");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Prompt metric unit is required");
        }
    }
}
