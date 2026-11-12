package io.clueterra.api.profile.dto.badgeProgress;

import java.util.List;

public record ProfileBadgeProgressDto(
        ProfileBadgeProgressKind kind,
        long current,
        long target,
        long remaining,
        int percent,
        String currentDisplay,
        String targetDisplay,
        String remainingDisplay,
        List<ProfileBadgeProgressItemDto> missingItems,
        int hiddenMissingCount,
        String hiddenMissingCountDisplay) {
}
