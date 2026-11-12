package io.clueterra.api.admin.analytics.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.clueterra.api.admin.analytics.dto.AggregationResultDto;
import io.clueterra.api.admin.analytics.dto.AnalyticsSummaryDto;
import io.clueterra.api.admin.analytics.dto.DailySessionDto;
import io.clueterra.api.admin.analytics.dto.SessionsByLevelDto;
import io.clueterra.api.admin.analytics.dto.TopCountryDto;
import io.clueterra.api.admin.analytics.service.AnalyticsService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Analytics", description = "Analytics from aggregated session data")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(analyticsService.getSummary(start, end));
    }

    @GetMapping("/by-level")
    public ResponseEntity<List<SessionsByLevelDto>> getByLevel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(analyticsService.getSessionsByLevel(start, end));
    }

    @GetMapping("/top-countries")
    public ResponseEntity<List<TopCountryDto>> getTopCountries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(analyticsService.getTopCountries(start, end, limit));
    }

    @GetMapping("/hardest-countries")
    public ResponseEntity<List<TopCountryDto>> getHardestCountries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(analyticsService.getHardestCountries(start, end, limit));
    }

    @GetMapping("/daily")
    public ResponseEntity<List<DailySessionDto>> getDailySessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(analyticsService.getDailySessions(start, end));
    }

    @PostMapping("/aggregate")
    public ResponseEntity<AggregationResultDto> aggregate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now().minusDays(1);
        return ResponseEntity.ok(analyticsService.aggregateRange(start, end));
    }
}
