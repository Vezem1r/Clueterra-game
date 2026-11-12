package io.clueterra.api.admin.dashboard.dto;

public record DashboardGameSummaryDto(
        long totalSessions,
        long completedSessions,
        long abandonedSessions,
        long uniqueUsers,
        long activeToday) {
}
