package io.clueterra.api.admin.badges.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record UserBadgeAdminDto(
        @JsonSerialize(using = ToStringSerializer.class) Long badgeId,
        String slug,
        String name,
        String imageUrl,
        String grantedAt,
        String grantedBy) {
}
