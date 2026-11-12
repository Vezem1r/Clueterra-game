package io.clueterra.api.projectpages.repository;

import java.math.BigDecimal;

public record ProjectPageMetricRow(
        String metricType,
        BigDecimal value,
        String unit,
        String sourceName,
        String sourceUrl) {
}
