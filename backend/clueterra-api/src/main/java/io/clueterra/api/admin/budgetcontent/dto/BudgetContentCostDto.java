package io.clueterra.api.admin.budgetcontent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BudgetContentCostDto(
        Long questionId,
        BigDecimal answerCostUsd,
        BigDecimal sourceAmount,
        String sourceCurrency,
        Integer costYear,
        String normalizationNote,
        String sourceName,
        String sourceUrl,
        String referenceUrl,
        LocalDate snapshotAt) {
}
