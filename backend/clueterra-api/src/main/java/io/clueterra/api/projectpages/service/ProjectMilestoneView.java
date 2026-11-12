package io.clueterra.api.projectpages.service;

public record ProjectMilestoneView(
        String label,
        String value,
        String sourceName,
        String sourceUrl) {
}
