package io.clueterra.budget.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "clueterra.budget.sync")
public record BudgetSyncProperties(@NotNull Duration staleRunTimeout) {

    @AssertTrue(message = "stale run timeout must be positive")
    public boolean isStaleRunTimeoutValid() {
        return staleRunTimeout == null || (!staleRunTimeout.isZero() && !staleRunTimeout.isNegative());
    }
}
