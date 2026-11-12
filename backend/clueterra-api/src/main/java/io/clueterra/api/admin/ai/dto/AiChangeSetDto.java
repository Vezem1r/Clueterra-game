package io.clueterra.api.admin.ai.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;
import java.util.Map;

public record AiChangeSetDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
        Long runId,
        String changeSetType,
        String status,
        String title,
        String summary,
        int totalItems,
        int appliedItems,
        int rejectedItems,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant appliedAt,
        Instant rejectedAt) {
}
