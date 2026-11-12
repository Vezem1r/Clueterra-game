package io.clueterra.api.projectpages.repository;

import java.math.BigDecimal;

public record ProjectDirectoryFilter(
        String search,
        String category,
        BigDecimal minimumCostUsd,
        BigDecimal maximumCostUsd,
        Integer minimumYear,
        Integer maximumYear) {

    public ProjectDirectoryFilter {
        search = search == null || search.isBlank() ? null : search.strip();
        category = category == null || category.isBlank() ? null : category.strip();
    }
}
