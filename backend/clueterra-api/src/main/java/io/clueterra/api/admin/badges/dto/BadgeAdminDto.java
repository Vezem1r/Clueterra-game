package io.clueterra.api.admin.badges.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record BadgeAdminDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String slug,
        String imageUrl,
        String accentColorKey,
        String effectColorKey,
        boolean isSecret,
        boolean isActive,
        String grantType,
        @JsonRawValue String grantConfig,
        int displayOrder,
        int ownerCount,
        Instant createdAt,
        Instant updatedAt,
        Map<String, BadgeTranslationDto> translations,
        @JsonSerialize(using = ToStringSerializer.class) Long categoryId,
        String categoryName) {
}
