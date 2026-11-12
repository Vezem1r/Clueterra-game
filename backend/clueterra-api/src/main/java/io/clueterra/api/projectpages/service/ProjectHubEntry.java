package io.clueterra.api.projectpages.service;

public record ProjectHubEntry(
        int rank,
        String slug,
        String name,
        String country,
        String category,
        Integer year,
        String costUsd,
        String answer,
        String costType,
        String costScope,
        boolean estimate,
        String status,
        ProjectRankingScope rankingScope,
        String imageUrl,
        String imageAlt) {
}
