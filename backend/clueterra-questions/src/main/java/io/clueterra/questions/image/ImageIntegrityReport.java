package io.clueterra.questions.image;

import java.util.List;
import java.util.Map;

public record ImageIntegrityReport(
        int totalItems,
        int successfulItems,
        int failedItems,
        Map<String, Object> summary,
        List<ImageAuditOutcome> outcomes) {
}
