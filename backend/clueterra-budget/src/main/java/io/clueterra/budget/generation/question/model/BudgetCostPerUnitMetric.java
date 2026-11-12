package io.clueterra.budget.generation.question.model;

import java.math.BigDecimal;

public record BudgetCostPerUnitMetric(
        BudgetQuestionMetricFact metric,
        BigDecimal denominator,
        BigDecimal displayValue,
        String displayUnit,
        String answerUnit) {
}
