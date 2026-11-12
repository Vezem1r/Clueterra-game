package io.clueterra.budget.generation.question.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.clueterra.budget.project.ProjectCostKind;

public record BudgetQuestionCostFact(
        long projectId,
        ProjectCostKind kind,
        BigDecimal sourceAmountLow,
        BigDecimal sourceAmountHigh,
        String sourceCurrency,
        BigDecimal amountUsdLow,
        BigDecimal amountUsdHigh,
        Integer costYear,
        BigDecimal unitsPerUsd,
        LocalDate exchangeRateDate,
        String exchangeRateBasis,
        String exchangeRateProvider,
        String evidenceText,
        String scopeNote,
        String sourceName,
        String sourceUrl,
        String referenceUrl) {

    private static final BigDecimal ONE_HALF = new BigDecimal("0.5");

    public BigDecimal midpointUsd() {
        return amountUsdLow.add(amountUsdHigh).multiply(ONE_HALF);
    }
}
