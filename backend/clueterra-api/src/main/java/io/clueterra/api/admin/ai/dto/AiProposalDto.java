package io.clueterra.api.admin.ai.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;
import java.util.List;

public record AiProposalDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
        Long runId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long stageId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long changeSetId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long countryId,
        String countryCode,
        String countryName,
        String status,
        String category,
        String hintType,
        String hintText,
        String imageSourceUrl,
        String storedImageUrl,
        Integer difficulty,
        Double qualityScore,
        String source,
        String sourceIndicator,
        Integer sourceYear,
        String factSummary,
        String evidenceSummary,
        String duplicateCheckSummary,
        List<String> searchTerms,
        Instant createdAt) {
}
