package io.clueterra.api.profile.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeProgressDto;

public record ProfileBadgeDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String slug,
        String imageUrl,
        String accentColorKey,
        String effectColorKey,
        String name,
        String description,
        boolean unlocked,
        String grantedAt,
        ProfileBadgeProgressDto progress) {
}
