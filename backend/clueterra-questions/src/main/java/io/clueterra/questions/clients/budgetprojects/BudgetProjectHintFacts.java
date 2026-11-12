package io.clueterra.questions.clients.budgetprojects;

import java.math.BigDecimal;

public record BudgetProjectHintFacts(
        String name,
        String category,
        String lifecycleStatus,
        Integer openingYear,
        BigDecimal costUsd,
        String costKind,
        String metricType,
        BigDecimal metricValue
) {
}
