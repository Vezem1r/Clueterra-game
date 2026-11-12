package io.clueterra.api.admin.console.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "clueterra.admin.console")
public record ConsoleProperties(@NotNull Duration ttl, @Min(1) int maxEntries) {
}
