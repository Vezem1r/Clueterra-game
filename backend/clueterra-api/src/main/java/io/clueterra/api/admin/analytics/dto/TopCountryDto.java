package io.clueterra.api.admin.analytics.dto;

public record TopCountryDto(
        long countryId,
        String countryCode,
        String countryName,
        long timesFeatured,
        long timesGuessedCorrect,
        double avgAttempts) {
}
