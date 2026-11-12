package io.clueterra.budget.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "clueterra.budget.enrichment")
public record BudgetEnrichmentProperties(
        @Min(1) @Max(250) int candidateImageBatchSize, @NotNull Duration processingTimeout) {

    @AssertTrue(message = "processing timeout must be positive")
    public boolean isProcessingTimeoutValid() {
        return processingTimeout == null
                || (!processingTimeout.isZero() && !processingTimeout.isNegative());
    }
}
