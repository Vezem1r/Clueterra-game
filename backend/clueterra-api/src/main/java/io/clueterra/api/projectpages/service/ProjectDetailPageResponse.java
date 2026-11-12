package io.clueterra.api.projectpages.service;

import java.util.List;

public record ProjectDetailPageResponse(
        ProjectDetailView project,
        List<ProjectCardView> relatedProjects) {
}
