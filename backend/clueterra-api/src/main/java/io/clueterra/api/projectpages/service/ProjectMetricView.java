package io.clueterra.api.projectpages.service;

public record ProjectMetricView(
        String label,
        String value,
        String sourceName,
        String sourceUrl) {
}
