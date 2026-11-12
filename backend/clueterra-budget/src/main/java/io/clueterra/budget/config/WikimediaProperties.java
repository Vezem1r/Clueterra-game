package io.clueterra.budget.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "clueterra.budget.source.wikimedia")
public record WikimediaProperties(
        boolean enabled,
        @NotBlank String apiUrl,
        @Min(1) @Max(50) int metadataBatchSize,
        @Min(640) @Max(2400) int thumbnailWidth,
        @Min(320) int minimumWidth,
        @Min(320) int minimumHeight,
        @Min(100_000) long maximumDownloadBytes,
        @Min(5) @Max(300) int maxLagSeconds,
        @NotNull Duration requestDelay) {

    @AssertTrue(message = "Wikimedia request delay must not be negative")
    public boolean isRequestDelayValid() {
        return requestDelay == null || !requestDelay.isNegative();
    }
}
