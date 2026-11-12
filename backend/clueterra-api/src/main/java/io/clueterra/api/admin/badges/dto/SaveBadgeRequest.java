package io.clueterra.api.admin.badges.dto;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public record SaveBadgeRequest(
        String slug,
        Boolean isSecret,
        Boolean isActive,
        String accentColorKey,
        String effectColorKey,
        String grantType,
        JsonNode grantConfig,
        Integer displayOrder,
        Long categoryId,
        Map<String, BadgeTranslationDto> translations) {
}
