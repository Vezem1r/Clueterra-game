package io.clueterra.questions.repository;

import java.math.BigDecimal;

public record IndicatorConfigRecord(
        String indicatorCode,
        String displayName,
        String category,
        String unit,
        BigDecimal weight,
        String profileLevel,
        boolean lowerIsBetter,
        boolean excludeFromRanking,
        boolean excludeFromPercentile,
        String hintTemplate,
        String hintType,
        Integer baseDifficulty,
        BigDecimal qualityScore,
        boolean highInterest
) {
    public boolean hasTemplate() {
        return hintTemplate != null && !hintTemplate.isBlank();
    }
}
