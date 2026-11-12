package io.clueterra.budget.generation.question.model;

import java.time.LocalDate;

import io.clueterra.budget.project.ProjectDatePrecision;
import io.clueterra.budget.project.ProjectMilestoneType;

public record BudgetQuestionMilestoneFact(
        long projectId,
        ProjectMilestoneType type,
        LocalDate date,
        Integer year,
        ProjectDatePrecision precision,
        String sourceName,
        String sourceUrl,
        String referenceUrl) {
}
