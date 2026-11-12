package io.clueterra.questions.ai.dto.changeset;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AiChangeSetActionResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long changeSetId,
        String status,
        int totalItems,
        int appliedItems,
        int rejectedItems,
        String message) {
}
