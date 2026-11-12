package io.clueterra.budget.generation.question.model;

import java.math.BigDecimal;

import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectLifecycleStatus;

public record BudgetSourceQuestion(
        long questionId,
        long projectId,
        String wikidataId,
        String name,
        String description,
        String descriptionSourceName,
        String descriptionSourceUrl,
        String descriptionLicenseLabel,
        String descriptionLicenseUrl,
        String countryCode,
        String countryName,
        String regionName,
        MegaprojectCategory category,
        Integer projectYear,
        ProjectCostKind answerCostKind,
        ProjectLifecycleStatus lifecycleStatus,
        Integer expectedCompletionYear,
        BigDecimal answerCostUsd,
        String costSourceName,
        String costSourceUrl,
        String costReferenceUrl,
        BudgetDifficulty difficulty) {
}
