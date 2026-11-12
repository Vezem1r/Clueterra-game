package io.clueterra.api.projectpages.repository;

public record ProjectPageFactRow(
        String label,
        String value,
        String sourceName,
        String sourceUrl) {
}
