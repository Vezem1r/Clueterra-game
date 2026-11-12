package io.clueterra.api.admin.ai.dto;

public record AiChangeSetActionResponse(
        String changeSetId,
        String status,
        int totalItems,
        int appliedItems,
        int rejectedItems,
        String message) {
}
