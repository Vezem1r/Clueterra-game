package io.clueterra.questions.ai.model.changeset;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;
import java.util.Map;

public record AiChangeItem(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
        Long changeSetId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long runId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long proposalId,
        AiChangeItemType itemType,
        AiChangeItemStatus status,
        @JsonSerialize(using = ToStringSerializer.class)
        Long hintId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long countryId,
        String reasonSummary,
        String diffSummary,
        Map<String, Object> originalSnapshot,
        Map<String, Object> proposedSnapshot,
        Instant createdAt,
        Instant decidedAt) {
}
