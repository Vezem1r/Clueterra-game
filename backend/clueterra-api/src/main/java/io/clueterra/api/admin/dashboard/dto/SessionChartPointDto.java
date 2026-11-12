package io.clueterra.api.admin.dashboard.dto;

import java.time.LocalDate;

public record SessionChartPointDto(
        LocalDate date,
        int totalSessions,
        int completedSessions,
        int uniqueUsers) {
}
