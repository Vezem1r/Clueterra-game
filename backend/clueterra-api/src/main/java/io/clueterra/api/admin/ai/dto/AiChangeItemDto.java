package io.clueterra.api.admin.ai.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;
import java.util.Map;

public record AiChangeItemDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
        Long changeSetId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long runId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long proposalId,
        String itemType,
        String status,
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
