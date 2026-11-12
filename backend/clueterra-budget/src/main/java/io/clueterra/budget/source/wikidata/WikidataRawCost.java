package io.clueterra.budget.source.wikidata;

import java.math.BigDecimal;

import io.clueterra.budget.project.ProjectCostKind;

public record WikidataRawCost(
        ProjectCostKind costKind,
        BigDecimal sourceAmountLow,
        BigDecimal sourceAmountHigh,
        String sourceCurrency,
        Integer costYear,
        String statementRank,
        String referenceUrl,
        String sourceName,
        String sourceUrl,
        String sourceKey,
        String scopeNote,
        String evidenceText) {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    public BigDecimal sourceAmount() {
        return midpoint(sourceAmountLow, sourceAmountHigh);
    }

    public WikidataRawCost prefer(WikidataRawCost candidate) {
        long candidateScore = score(candidate);
        long currentScore = score(this);
        if (candidateScore != currentScore) {
            return candidateScore > currentScore ? candidate : this;
        }
        return candidate.sourceAmount().compareTo(sourceAmount()) > 0 ? candidate : this;
    }

    private long score(WikidataRawCost value) {
        long sourceScore = value.costKind().answerPriority() * 1_000_000_000L;
        long rankScore = value.statementRank().endsWith("PreferredRank") ? 100_000_000L : 0L;
        long referenceScore = value.referenceUrl().isBlank() ? 0L : 100_000_000L;
        long yearScore = value.costYear() == null ? 0L : value.costYear() * 10_000L;
        return sourceScore + rankScore + referenceScore + yearScore;
    }

    private BigDecimal midpoint(BigDecimal low, BigDecimal high) {
        return low.add(high).multiply(HALF);
    }
}
