package io.clueterra.budget.generation.question.model;

import java.math.BigDecimal;
import java.util.Objects;

import io.clueterra.budget.project.ProjectMetricType;

public record BudgetPromptMetric(ProjectMetricType type, BigDecimal value, String unit) {

    public BudgetPromptMetric {
        Objects.requireNonNull(type, "Prompt metric type is required");
        if (type == ProjectMetricType.MAIN_SPAN) {
            throw new IllegalArgumentException("Main span is not supported in cost-per-unit prompts");
        }
        Objects.requireNonNull(value, "Prompt metric value is required");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Prompt metric value must be positive");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Prompt metric unit is required");
        }
    }
}
