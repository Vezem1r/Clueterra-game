package io.clueterra.questions.ai.tooling.dto;

public record AiStoredImageToolResponse(
        boolean stored,
        String sourceUrl,
        String storedUrl,
        String message) {
}
