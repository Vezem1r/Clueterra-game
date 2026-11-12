package io.clueterra.budget.generation.question.model;

import java.math.BigDecimal;

import io.clueterra.budget.project.ProjectMetricType;

public record BudgetQuestionMetricFact(
        long projectId,
        ProjectMetricType type,
        BigDecimal normalizedValue,
        String normalizedUnit,
        String sourceName,
        String sourceUrl,
        String referenceUrl) {
}
