package io.clueterra.api.admin.synslogs.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record DataSyncLogAdminDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String source,
        String syncType,
        String status,
        int countriesProcessed,
        int statisticsSaved,
        int hintsGenerated,
        int hintsDeactivated,
        String errorMessage,
        String startedAt,
        String completedAt,
        Long durationMs,
        @JsonSerialize(using = ToStringSerializer.class) Long batchId,
        String countryCode,
        String errorStack,
        String fetchDetails) {
}
