package io.clueterra.api.admin.console.dto;

public record OperationStatusDto(
        String id,
        String name,
        String status,
        long startedAt,
        Long completedAt,
        String error) {
}
