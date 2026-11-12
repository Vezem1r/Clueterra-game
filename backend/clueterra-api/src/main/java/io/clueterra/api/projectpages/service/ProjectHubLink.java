package io.clueterra.api.projectpages.service;

public record ProjectHubLink(
        String path,
        ProjectHubKind kind,
        String category,
        String countryCode,
        String countryName,
        long projectCount) {
}
