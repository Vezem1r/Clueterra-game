package io.clueterra.questions.service.enrichment.dto;

import io.clueterra.questions.entity.enums.DataSource;

public record IndicatorKey(DataSource source, String indicatorCode, Integer year) {
}
