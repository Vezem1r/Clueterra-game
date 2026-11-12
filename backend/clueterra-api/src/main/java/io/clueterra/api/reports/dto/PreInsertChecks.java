package io.clueterra.api.reports.dto;

public record PreInsertChecks(boolean alreadyReported, long pendingCount, boolean isMuted, boolean hintExists) {
}
