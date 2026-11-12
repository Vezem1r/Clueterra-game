package io.clueterra.api.admin.reports.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AdminReportDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long hintId,
        @JsonSerialize(using = ToStringSerializer.class) Long userId,
        String userEmail,
        String hintText,
        String countryName,
        String reportType,
        String description,
        String status,
        String adminNotes,
        @JsonSerialize(using = ToStringSerializer.class) Long resolvedById,
        String resolvedByEmail,
        String createdAt,
        String resolvedAt) {
}
