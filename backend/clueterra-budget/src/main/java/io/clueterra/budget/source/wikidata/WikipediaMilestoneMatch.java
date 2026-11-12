package io.clueterra.budget.source.wikidata;

import java.time.LocalDate;

import io.clueterra.budget.project.ProjectDatePrecision;
import io.clueterra.budget.project.ProjectMilestoneType;

public record WikipediaMilestoneMatch(
        ProjectMilestoneType milestoneType,
        LocalDate milestoneDate,
        Integer milestoneYear,
        ProjectDatePrecision datePrecision,
        String referenceUrl,
        String evidenceText,
        String fieldName) {
}
