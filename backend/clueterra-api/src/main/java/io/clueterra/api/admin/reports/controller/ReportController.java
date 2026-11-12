package io.clueterra.api.admin.reports.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.reports.dto.AdminReportDto;
import io.clueterra.api.admin.reports.dto.ResolveReportRequest;
import io.clueterra.api.admin.reports.service.ReportService;
import io.clueterra.api.security.UserPrincipal;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Reports", description = "Hint report management")
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<Page<AdminReportDto>> listReports(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String excludeStatus,
            @RequestParam(required = false) Long hintId,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(reportService.getReports(status, excludeStatus, hintId, sortBy, sortDir, page, size));
    }

    @GetMapping("/count-pending")
    public ResponseEntity<Map<String, Long>> countPendingForHint(@RequestParam Long hintId) {
        long count = reportService.countPendingByHintId(hintId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminReportDto> getReport(@PathVariable Long id) {
        return reportService.getReport(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminReportDto> resolveReport(
            @PathVariable Long id,
            @RequestBody ResolveReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return reportService.resolveReport(id, request, principal.getId()).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
