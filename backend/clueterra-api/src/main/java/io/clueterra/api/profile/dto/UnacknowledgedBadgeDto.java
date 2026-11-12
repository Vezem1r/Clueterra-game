package io.clueterra.api.profile.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record UnacknowledgedBadgeDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long badgeId,
        String slug,
        String imageUrl,
        String accentColorKey,
        String effectColorKey,
        String name,
        String description,
        String grantedAt) {
}
