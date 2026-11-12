package io.clueterra.api.admin.badges.dto;

public record BadgeTranslationDto(
        String name,
        String descriptionUnlocked,
        String descriptionLocked) {
}
