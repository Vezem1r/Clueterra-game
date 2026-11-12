package io.clueterra.api.admin.ai.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;
import java.util.Map;

public record AiRunStageDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
        Long runId,
        int stageIndex,
        String stageType,
        String status,
        int itemCount,
        long estimatedInputTokens,
        long estimatedOutputTokens,
        long actualInputTokens,
        long actualOutputTokens,
        int imageCount,
        Map<String, Object> payload,
        Map<String, Object> resultSummary,
        String errorMessage,
        String providerRequestId,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {
}
