package io.clueterra.api.admin.badges.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record BadgeCategoryDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        int displayOrder,
        int badgeCount) {
}
