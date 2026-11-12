package io.clueterra.api.admin.reports.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import io.clueterra.api.admin.reports.dto.AdminReportDto;
import io.clueterra.api.admin.reports.dto.ResolveReportRequest;
import io.clueterra.api.admin.reports.repository.AdminReportJdbcRepository;
import io.clueterra.api.common.enums.ReportStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service("adminReportService")
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final AdminReportJdbcRepository reportRepo;

    public Page<AdminReportDto> getReports(String status, String excludeStatus, Long hintId, String sortBy, String sortDir, int page, int size) {
        return reportRepo.findAll(status, excludeStatus, hintId, sortBy, sortDir, PageRequest.of(page, size));
    }

    public Optional<AdminReportDto> getReport(Long id) {
        return reportRepo.findById(id);
    }

    public long countPendingByHintId(Long hintId) {
        return reportRepo.countPendingByHintId(hintId);
    }

    public Optional<AdminReportDto> resolveReport(Long id, ResolveReportRequest request, Long adminId) {
        Optional<AdminReportDto> existing = reportRepo.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        if (!ReportStatus.PENDING.name().equals(existing.get().status())) {
            throw new IllegalStateException("Report is already resolved");
        }

        ReportStatus newStatus = request.status();
        if (newStatus == ReportStatus.PENDING) {
            throw new IllegalStateException("Cannot set status back to PENDING");
        }

        if (Boolean.TRUE.equals(request.applyToAll())) {
            reportRepo.resolveAllPendingByHintId(existing.get().hintId(), newStatus.name(), request.adminNotes(), adminId);
            log.info("Reports resolved for hintId={} (status={}, adminId={})", existing.get().hintId(), newStatus, adminId);
        } else {
            int rows = reportRepo.resolve(id, newStatus.name(), request.adminNotes(), adminId);
            if (rows == 0) {
                return Optional.empty();
            }
            log.info("Report resolved: reportId={}, status={}, adminId={}", id, newStatus, adminId);
        }

        return reportRepo.findById(id);
    }
}
