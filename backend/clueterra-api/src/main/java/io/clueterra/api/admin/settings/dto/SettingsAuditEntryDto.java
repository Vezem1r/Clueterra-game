package io.clueterra.api.admin.settings.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record SettingsAuditEntryDto(
        String batchId,
        Instant changedAt,
        @JsonSerialize(using = ToStringSerializer.class) Long changedByUserId,
        String changedByEmail,
        String changedByUsername,
        List<SettingsAuditChangeDto> changes) {
}
