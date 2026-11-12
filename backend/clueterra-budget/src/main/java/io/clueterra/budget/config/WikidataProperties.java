package io.clueterra.budget.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "clueterra.budget.source.wikidata")
public record WikidataProperties(
        boolean enabled,
        @NotBlank String sparqlUrl,
        @NotBlank String entityApiUrl,
        @NotBlank String wikipediaApiUrl,
        @NotNull @Size(max = 10) List<@Pattern(regexp = "^[a-z]{2,3}$") String>
                wikipediaFallbackLanguages,
        @Min(20) int queryPageSize,
        @Min(5) @Max(100) int maximumCountrySharePercent,
        @Min(25) @Max(100) int maximumRegionSharePercent,
        @Min(10) @Max(89) int easySharePercent,
        @Min(20) @Max(89) int mediumSharePercent,
        @Min(1) int minimumSitelinks,
        @Min(0) @Max(100) int minimumQualityScore,
        @Min(1) @Max(25) int entityBatchSize,
        @Min(1) @Max(25) int minimumRecoveryBatchSize,
        @Min(1) @Max(20) int wikipediaArticleBatchSize,
        @Min(7) @Max(60) int pageviewDays,
        @Min(5) @Max(300) int maxLagSeconds,
        @Min(1) @Max(8) int batchMaxAttempts,
        @NotNull Duration batchRecoveryDelay,
        @NotNull Duration batchRecoveryMaxDelay,
        @NotNull @DecimalMin("1000000") BigDecimal minimumProjectCostUsd,
        @NotNull @DecimalMax("1000000000000") BigDecimal maximumProjectCostUsd,
        @NotNull Duration requestDelay) {

    public WikidataProperties {
        wikipediaFallbackLanguages =
                wikipediaFallbackLanguages == null
                        ? List.of()
                        : List.copyOf(
                                new LinkedHashSet<>(
                                        wikipediaFallbackLanguages.stream()
                                                .filter(Objects::nonNull)
                                                .map(value -> value.toLowerCase(Locale.ROOT))
                                                .filter(value -> !"en".equals(value))
                                                .toList()));
    }

    @AssertTrue(message = "easy and medium difficulty shares must leave room for hard candidates")
    public boolean isDifficultyDistributionValid() {
        return easySharePercent + mediumSharePercent < 100;
    }

    @AssertTrue(message = "Wikidata request delay must not be negative")
    public boolean isRequestDelayValid() {
        return requestDelay == null || !requestDelay.isNegative();
    }

    @AssertTrue(message = "Wikidata batch recovery delay must not be negative")
    public boolean isBatchRecoveryDelayValid() {
        return batchRecoveryDelay == null || !batchRecoveryDelay.isNegative();
    }

    @AssertTrue(message = "Wikidata batch recovery maximum delay must be positive")
    public boolean isBatchRecoveryMaximumDelayValid() {
        return batchRecoveryMaxDelay == null
                || (!batchRecoveryMaxDelay.isZero() && !batchRecoveryMaxDelay.isNegative());
    }

    @AssertTrue(message = "Wikidata batch recovery delay must not exceed its maximum")
    public boolean isBatchRecoveryDelayRangeValid() {
        return batchRecoveryDelay == null
                || batchRecoveryMaxDelay == null
                || batchRecoveryDelay.compareTo(batchRecoveryMaxDelay) <= 0;
    }

    @AssertTrue(message = "minimum recovery batch size must not exceed entity batch size")
    public boolean isRecoveryBatchSizeValid() {
        return minimumRecoveryBatchSize <= entityBatchSize;
    }

    @AssertTrue(message = "minimum project cost must not exceed maximum project cost")
    public boolean isProjectCostRangeValid() {
        return minimumProjectCostUsd == null
                || maximumProjectCostUsd == null
                || minimumProjectCostUsd.compareTo(maximumProjectCostUsd) <= 0;
    }
}
