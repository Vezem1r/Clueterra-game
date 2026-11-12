package io.clueterra.api.projectpages.service;

import java.util.List;

public record ProjectDirectoryPage(
        List<ProjectCardView> projects,
        int pageNumber,
        int pageSize,
        long totalProjects,
        int totalPages,
        List<ProjectHubLink> countryHubs) {

    public boolean hasPrevious() {
        return pageNumber > 1;
    }

    public boolean hasNext() {
        return pageNumber < totalPages;
    }

    public int previousPage() {
        return pageNumber - 1;
    }

    public int nextPage() {
        return pageNumber + 1;
    }

    public int firstPosition() {
        return (pageNumber - 1) * pageSize + 1;
    }
}
