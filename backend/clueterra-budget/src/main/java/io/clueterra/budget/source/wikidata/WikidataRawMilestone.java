package io.clueterra.budget.source.wikidata;

import java.time.LocalDate;

import io.clueterra.budget.project.ProjectDatePrecision;
import io.clueterra.budget.project.ProjectMilestoneType;

public record WikidataRawMilestone(
        ProjectMilestoneType milestoneType,
        LocalDate milestoneDate,
        Integer milestoneYear,
        ProjectDatePrecision datePrecision,
        String sourceName,
        String sourceUrl,
        String referenceUrl,
        String sourceKey,
        String evidenceText,
        boolean primary) {
}
