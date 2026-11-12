package io.clueterra.budget.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "clueterra.budget.generation")
public record BudgetGenerationProperties(
        @Min(0) int recentProjectExclusionDays,
        boolean schedulerEnabled,
        @NotBlank String dailyCron) {}
