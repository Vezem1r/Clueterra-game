package io.clueterra.api.budget.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BudgetPromptProjectDto(
        String name,
        String countryCode,
        String countryName,
        String regionName,
        BudgetCategory category,
        BudgetProjectLifecycleStatus lifecycleStatus,
        Integer projectYear,
        Integer completionYear,
        Integer openingYear,
        Integer expectedCompletionYear,
        String answerCostKind) {

    public BudgetPromptProjectDto {
        requireText(name, "Prompt project name is required");
        requireText(countryCode, "Prompt project country code is required");
        requireText(countryName, "Prompt project country name is required");
        Objects.requireNonNull(category, "Prompt project category is required");
        lifecycleStatus = lifecycleStatus == null
                ? BudgetProjectLifecycleStatus.UNKNOWN
                : lifecycleStatus;
        answerCostKind = answerCostKind == null || answerCostKind.isBlank()
                ? "REPORTED_COST"
                : answerCostKind;
    }

    public BudgetPromptProjectDto(
            String name,
            String countryCode,
            String countryName,
            String regionName,
            BudgetCategory category,
            Integer completionYear) {
        this(
                name,
                countryCode,
                countryName,
                regionName,
                category,
                BudgetProjectLifecycleStatus.UNKNOWN,
                null,
                completionYear,
                null,
                null,
                "REPORTED_COST");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
