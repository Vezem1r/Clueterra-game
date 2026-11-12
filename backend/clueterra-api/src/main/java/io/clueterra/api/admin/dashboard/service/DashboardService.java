package io.clueterra.api.admin.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.clueterra.api.admin.dashboard.dto.DashboardSummaryDto;
import io.clueterra.api.admin.dashboard.dto.SessionChartPointDto;
import io.clueterra.api.admin.dashboard.repository.DashboardJdbcRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardJdbcRepository dashboardRepo;

    public DashboardSummaryDto getSummary() {
        return dashboardRepo.getDashboardSummary();
    }

    public List<SessionChartPointDto> getSessionsChart(int days) {
        return dashboardRepo.getSessionsOverTime(days);
    }

    public List<SessionChartPointDto> getBudgetSessionsChart(int days) {
        return dashboardRepo.getBudgetSessionsOverTime(days);
    }
}
