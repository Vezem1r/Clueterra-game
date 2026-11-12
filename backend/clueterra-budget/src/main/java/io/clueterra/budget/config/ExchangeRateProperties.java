package io.clueterra.budget.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "clueterra.budget.source.exchange-rates")
public record ExchangeRateProperties(
        boolean enabled, @NotBlank String apiUrl, @NotNull Duration cacheDuration) {

    @AssertTrue(message = "exchange-rate cache duration must be positive")
    public boolean isCacheDurationValid() {
        return cacheDuration == null || (!cacheDuration.isZero() && !cacheDuration.isNegative());
    }
}
