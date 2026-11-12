package io.clueterra.api.projectpages.service;

import java.time.LocalDate;

public record ProjectCardView(
        String slug,
        String name,
        String country,
        String category,
        Integer year,
        String answer,
        String usdEquivalent,
        String description,
        String imageUrl,
        String imageAlt,
        LocalDate updatedDate) {
}
