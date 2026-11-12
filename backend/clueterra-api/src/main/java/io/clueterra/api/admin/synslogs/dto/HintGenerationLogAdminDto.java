package io.clueterra.api.admin.synslogs.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record HintGenerationLogAdminDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long countryId,
        String countryCode,
        String triggerType,
        int hintsAttempted,
        int hintsCreated,
        int hintsDuplicates,
        int hintsFailed,
        BigDecimal avgDifficulty,
        int categoriesCovered,
        int hintTypesCovered,
        String startedAt,
        String completedAt,
        Long durationMs,
        String errorMessage,
        @JsonSerialize(using = ToStringSerializer.class) Long batchId,
        BigDecimal avgQualityScore,
        Map<String, Object> details,
        Map<String, Object> lensBreakdown) {
}
