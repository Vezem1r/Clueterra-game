package io.clueterra.questions.image;

import java.util.List;
import java.util.Map;

public record ImageAuditOutcome(
        ImageAuditCandidate candidate,
        boolean storageMissing,
        boolean restored,
        String repairFailure,
        ImageSourceValidation validation,
        List<String> metadataChanges,
        boolean metadataBackfilled,
        String objectPath,
        Map<String, Object> currentMetadata) {

    public boolean hasActionableIssue() {
        return repairFailure != null
                || validation.status() == ImageSourceValidationStatus.SOURCE_MISSING
                || validation.status() == ImageSourceValidationStatus.RESTRICTED_LICENSE
                || validation.status() == ImageSourceValidationStatus.FETCH_FAILED;
    }

    public boolean hasReportableResult() {
        return hasActionableIssue() || restored || metadataBackfilled || !metadataChanges.isEmpty();
    }
}
