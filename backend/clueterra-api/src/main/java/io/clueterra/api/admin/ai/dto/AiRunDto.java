package io.clueterra.api.admin.ai.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;
import java.util.Map;

public record AiRunDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String runType,
        String triggerType,
        String status,
        String provider,
        String model,
        String promptVersion,
        String requestedBy,
        int totalStages,
        int preparedItems,
        int processedItems,
        int changedItems,
        int generatedItems,
        long estimatedInputTokens,
        long estimatedOutputTokens,
        long actualInputTokens,
        long actualOutputTokens,
        int toolCallCount,
        Map<String, Object> configuration,
        Map<String, Object> summary,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {
}
