package io.clueterra.questions.ai.tooling.dto;

import java.time.Instant;

public record AiGeneratedHintProposalToolSummary(
        long id,
        String status,
        String category,
        String hintType,
        String hintText,
        String imageUrl,
        Integer difficulty,
        Double qualityScore,
        String sourceIndicator,
        Instant createdAt) {
}
