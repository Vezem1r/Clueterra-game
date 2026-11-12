package io.clueterra.api.reports.dto;

import io.clueterra.api.common.enums.ReportType;
import jakarta.validation.constraints.NotNull;

public record HintReportRequest(
        @NotNull(message = "Hint ID is required") Long hintId,
        @NotNull(message = "Report type is required") ReportType reportType,
        String description) {
}
