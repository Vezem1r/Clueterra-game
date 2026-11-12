package io.clueterra.api.admin.dashboard.dto;

public record DashboardSummaryDto(
        DashboardGameSummaryDto geography,
        DashboardGameSummaryDto budget,
        long totalRegisteredUsers) {
}
