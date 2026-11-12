package io.clueterra.api.reports.service;

import io.clueterra.api.reports.dto.PreInsertChecks;
import io.clueterra.api.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.clueterra.api.reports.dto.HintReportRequest;
import io.clueterra.api.reports.dto.HintReportResponse;
import io.clueterra.api.reports.repository.HintReportJdbcRepository;

import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HintReportService {

    private final HintReportJdbcRepository reportRepository;

    @Value("${clueterra.reports.max-pending-per-user:5}")
    private int maxPendingPerUser;

    private static final String SUCCESS_MESSAGE = "Report submitted successfully";

    public HintReportResponse report(Long userId, HintReportRequest request) {
        PreInsertChecks checks = reportRepository.preInsertChecks(request.hintId(), userId);

        if (!checks.hintExists()) {
            throw new ResourceNotFoundException("Hint not found");
        }

        if (checks.alreadyReported()) {
            throw new IllegalArgumentException("You have already reported this hint");
        }

        if (checks.pendingCount() >= maxPendingPerUser) {
            throw new IllegalStateException(
                    "You have too many pending reports. Please wait until some are reviewed.");
        }

        if (checks.isMuted()) {
            String fakeId = String.valueOf(TSID.Factory.getTsid().toLong());
            return new HintReportResponse(true, SUCCESS_MESSAGE, fakeId);
        }

        long reportId = reportRepository.insert(
                request.hintId(), userId, request.reportType().name(), request.description());
        return new HintReportResponse(true, SUCCESS_MESSAGE, String.valueOf(reportId));
    }
}
