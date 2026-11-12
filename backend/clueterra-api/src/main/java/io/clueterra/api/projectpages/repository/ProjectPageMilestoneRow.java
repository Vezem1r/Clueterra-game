package io.clueterra.api.projectpages.repository;

import java.time.LocalDate;

public record ProjectPageMilestoneRow(
        String milestoneType,
        LocalDate dateValue,
        Integer yearValue,
        String sourceName,
        String sourceUrl) {
}
