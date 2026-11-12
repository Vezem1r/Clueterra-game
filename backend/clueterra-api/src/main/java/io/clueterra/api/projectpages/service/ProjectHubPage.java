package io.clueterra.api.projectpages.service;

import java.util.List;

public record ProjectHubPage(
        ProjectHubKind kind,
        String path,
        String category,
        String countryCode,
        String countryName,
        long totalProjects,
        List<ProjectHubEntry> projects,
        List<ProjectHubLink> categoryHubs,
        List<ProjectHubLink> countryHubs) {
}
