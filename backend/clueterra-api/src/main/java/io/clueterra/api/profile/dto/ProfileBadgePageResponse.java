package io.clueterra.api.profile.dto;

import java.util.List;

public record ProfileBadgePageResponse(
        List<ProfileBadgeDto> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        long earnedCount,
        long lockedVisibleCount,
        int hiddenSecretCount) {
}
