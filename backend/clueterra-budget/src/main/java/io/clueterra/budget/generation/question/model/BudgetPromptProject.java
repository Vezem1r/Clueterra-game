package io.clueterra.budget.generation.question.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectLifecycleStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BudgetPromptProject(
        String name,
        String countryCode,
        String countryName,
        String regionName,
        MegaprojectCategory category,
        ProjectLifecycleStatus lifecycleStatus,
        Integer projectYear,
        Integer completionYear,
        Integer openingYear,
        Integer expectedCompletionYear,
        ProjectCostKind answerCostKind) {

    public BudgetPromptProject {
        requireText(name, "Prompt project name is required");
        requireText(countryCode, "Prompt project country code is required");
        requireText(countryName, "Prompt project country name is required");
        Objects.requireNonNull(category, "Prompt project category is required");
        lifecycleStatus = lifecycleStatus == null ? ProjectLifecycleStatus.UNKNOWN : lifecycleStatus;
        answerCostKind = answerCostKind == null ? ProjectCostKind.REPORTED_COST : answerCostKind;
    }

    public BudgetPromptProject(
            String name,
            String countryCode,
            String countryName,
            String regionName,
            MegaprojectCategory category,
            Integer completionYear) {
        this(
                name,
                countryCode,
                countryName,
                regionName,
                category,
                ProjectLifecycleStatus.UNKNOWN,
                null,
                completionYear,
                null,
                null,
                ProjectCostKind.REPORTED_COST);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
