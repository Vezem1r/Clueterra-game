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
@ConfigurationProperties(prefix = "clueterra.budget.http")
public record ExternalHttpProperties(
        @NotBlank String userAgent,
        @Min(1) @Max(5) int maxAttempts,
        @NotNull Duration retryBaseDelay,
        @NotNull Duration retryMaxDelay,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration callTimeout) {

    @AssertTrue(message = "HTTP durations must be positive")
    public boolean areDurationsValid() {
        return isPositive(retryBaseDelay)
                && isPositive(retryMaxDelay)
                && isPositive(connectTimeout)
                && isPositive(readTimeout)
                && isPositive(callTimeout);
    }

    @AssertTrue(message = "retry base delay must not exceed retry max delay")
    public boolean isRetryRangeValid() {
        return retryBaseDelay == null
                || retryMaxDelay == null
                || retryBaseDelay.compareTo(retryMaxDelay) <= 0;
    }

    @AssertTrue(message = "call timeout must cover connect and read timeouts")
    public boolean isCallTimeoutValid() {
        return callTimeout == null
                || connectTimeout == null
                || readTimeout == null
                || (callTimeout.compareTo(connectTimeout) >= 0 && callTimeout.compareTo(readTimeout) >= 0);
    }

    private boolean isPositive(Duration duration) {
        return duration == null || (!duration.isZero() && !duration.isNegative());
    }
}
