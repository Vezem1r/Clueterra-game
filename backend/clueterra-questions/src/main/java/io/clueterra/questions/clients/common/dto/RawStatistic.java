package io.clueterra.questions.clients.common.dto;

import io.clueterra.questions.entity.enums.Category;

import java.math.BigDecimal;
import java.util.Objects;

public record RawStatistic(
        String indicatorCode,
        String indicatorName,
        Category category,
        BigDecimal value,
        String valueText,
        String unit,
        Integer year,
        BigDecimal confidence,
        BigDecimal weight,
        String imageUrl,
        String originalImageUrl,
        String imageSourcePageUrl,
        String imageAuthorName,
        String imageAttributionText,
        String imageLicenseLabel,
        String imageLicenseUrl
) {
    public RawStatistic {
        Objects.requireNonNull(indicatorCode, "indicatorCode is required");
    }

    public RawStatistic(String indicatorCode, String indicatorName, Category category,
                        BigDecimal value, String valueText, String unit, Integer year,
                        BigDecimal confidence, BigDecimal weight, String imageUrl, String originalImageUrl) {
        this(indicatorCode, indicatorName, category, value, valueText, unit, year, confidence, weight,
                imageUrl, originalImageUrl, null, null, null, null, null);
    }

    public RawStatistic(String indicatorCode, String indicatorName, Category category,
                        BigDecimal value, String valueText, String unit, Integer year,
                        BigDecimal confidence, BigDecimal weight) {
        this(indicatorCode, indicatorName, category, value, valueText, unit, year, confidence, weight,
                null, null, null, null, null, null, null);
    }

    public RawStatistic(String indicatorCode, String indicatorName, Category category,
                        BigDecimal value, String valueText, String unit, Integer year,
                        BigDecimal confidence, BigDecimal weight, String imageUrl) {
        this(indicatorCode, indicatorName, category, value, valueText, unit, year, confidence, weight,
                imageUrl, null, null, null, null, null, null);
    }

    public boolean hasValue() {
        return value != null;
    }

    public BigDecimal getWeightOrDefault() {
        return weight != null ? weight : BigDecimal.valueOf(0.5);
    }

    public BigDecimal getConfidenceOrDefault() {
        return confidence != null ? confidence : BigDecimal.ONE;
    }

    public static RawStatistic of(String code, String name, Category category, BigDecimal value, BigDecimal weight) {
        return new RawStatistic(code, name, category, value, null, null, null, BigDecimal.ONE, weight);
    }
}
