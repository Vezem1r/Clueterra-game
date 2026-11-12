package io.clueterra.budget.source.wikidata;

import java.math.BigDecimal;

import io.clueterra.budget.project.ProjectCostKind;

public record WikipediaCostMatch(
        ProjectCostKind costKind,
        BigDecimal amountLow,
        BigDecimal amountHigh,
        String currency,
        Integer costYear,
        String referenceUrl,
        String evidenceText,
        String fieldName,
        String scopeNote) {

}
