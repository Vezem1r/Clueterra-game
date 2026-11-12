package io.clueterra.budget.project;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WikidataProjectCost(
        ProjectCostKind costKind,
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
        String sourceName,
        String sourceUrl,
        String referenceUrl,
        String normalizationNote,
        String scopeNote,
        String sourceKey,
        String evidenceText,
        ProjectCostVerificationStatus verificationStatus,
        boolean primary,
        boolean answer) {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    public BigDecimal sourceAmount() {
        return midpoint(sourceAmountLow, sourceAmountHigh);
    }

    public BigDecimal amountUsd() {
        return midpoint(amountUsdLow, amountUsdHigh);
    }

    private BigDecimal midpoint(BigDecimal low, BigDecimal high) {
        return low.add(high).multiply(HALF);
    }
}
