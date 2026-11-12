package io.clueterra.api.admin.analytics.dto;

public record AnalyticsSummaryDto(
        long totalSessions,
        long completedSessions,
        long abandonedSessions,
        long uniqueUsers,
        long totalGuesses,
        long correctGuesses,
        double avgAttemptsPerCountry,
        double avgTimePerSessionMs) {
}
