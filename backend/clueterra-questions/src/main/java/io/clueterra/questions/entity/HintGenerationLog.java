package io.clueterra.questions.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record HintGenerationLog(
        Long id,
        Long countryId,
        Long batchId,
        String countryCode,
        HintGenerationTriggerType triggerType,
        int hintsAttempted,
        int hintsCreated,
        int hintsDuplicates,
        int hintsFailed,
        BigDecimal avgDifficulty,
        BigDecimal avgQualityScore,
        int categoriesCovered,
        int hintTypesCovered,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        String errorMessage,
        Map<String, Object> details,
        Map<String, Object> lensBreakdown
) {
    public static HintGenerationLogBuilder builder() {
        return new HintGenerationLogBuilder();
    }

}
