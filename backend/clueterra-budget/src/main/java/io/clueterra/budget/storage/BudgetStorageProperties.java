package io.clueterra.budget.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "clueterra.storage")
public record BudgetStorageProperties(
        boolean enabled,
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey) {
}
