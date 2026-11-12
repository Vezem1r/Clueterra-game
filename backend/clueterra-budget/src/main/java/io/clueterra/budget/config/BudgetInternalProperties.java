package io.clueterra.budget.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "clueterra.budget.internal")
public record BudgetInternalProperties(@NotBlank String secret) {
}
