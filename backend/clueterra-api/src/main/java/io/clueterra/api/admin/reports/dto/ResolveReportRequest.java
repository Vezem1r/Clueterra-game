package io.clueterra.api.admin.reports.dto;

import io.clueterra.api.common.enums.ReportStatus;

public record ResolveReportRequest(ReportStatus status, String adminNotes, Boolean applyToAll) {
}
