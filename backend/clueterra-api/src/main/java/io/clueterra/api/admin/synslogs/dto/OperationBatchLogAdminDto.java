package io.clueterra.api.admin.synslogs.dto;

import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record OperationBatchLogAdminDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String operationType,
        String triggerType,
        String status,
        int totalItems,
        int successfulItems,
        int failedItems,
        String startedAt,
        String completedAt,
        Long durationMs,
        String errorMessage,
        Map<String, Object> summary) {
}
