package io.clueterra.budget.source.wikidata;

import java.math.BigDecimal;

import io.clueterra.budget.project.ProjectMetricType;

public record WikipediaMetricMatch(
        ProjectMetricType metricType,
        BigDecimal normalizedValue,
        String normalizedUnit,
        BigDecimal sourceValue,
        String sourceUnit,
        String referenceUrl,
        String evidenceText,
        String fieldName) {
}
