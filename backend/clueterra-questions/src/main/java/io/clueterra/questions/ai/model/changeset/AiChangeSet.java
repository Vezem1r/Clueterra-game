package io.clueterra.questions.ai.model.changeset;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;
import java.util.Map;

public record AiChangeSet(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
        Long runId,
        AiChangeSetType changeSetType,
        AiChangeSetStatus status,
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
