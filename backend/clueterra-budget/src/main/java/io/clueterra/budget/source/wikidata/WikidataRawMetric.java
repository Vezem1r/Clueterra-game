package io.clueterra.budget.source.wikidata;

import java.math.BigDecimal;

import io.clueterra.budget.project.ProjectMetricType;

public record WikidataRawMetric(
        ProjectMetricType metricType,
        BigDecimal normalizedValue,
        String normalizedUnit,
        BigDecimal sourceValue,
        String sourceUnit,
        String statementRank,
        String sourceName,
        String sourceUrl,
        String referenceUrl,
        String sourceKey,
        String evidenceText,
        boolean primary) {
}
