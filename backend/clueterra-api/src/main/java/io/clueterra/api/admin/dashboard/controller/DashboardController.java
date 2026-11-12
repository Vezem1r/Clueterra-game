package io.clueterra.api.admin.dashboard.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.clueterra.api.admin.dashboard.dto.DashboardSummaryDto;
import io.clueterra.api.admin.dashboard.dto.SessionChartPointDto;
import io.clueterra.api.admin.dashboard.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Dashboard", description = "Dashboard analytics")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "Get the geography and BudgetGuessr dashboard summary")
    public ResponseEntity<DashboardSummaryDto> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @GetMapping("/sessions-chart")
    @Operation(summary = "Get geography game sessions over time")
    public ResponseEntity<List<SessionChartPointDto>> getSessionsChart(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return ResponseEntity.ok(dashboardService.getSessionsChart(days));
    }

    @GetMapping("/budget-sessions-chart")
    @Operation(summary = "Get BudgetGuessr sessions over time")
    public ResponseEntity<List<SessionChartPointDto>> getBudgetSessionsChart(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return ResponseEntity.ok(dashboardService.getBudgetSessionsChart(days));
    }
}
