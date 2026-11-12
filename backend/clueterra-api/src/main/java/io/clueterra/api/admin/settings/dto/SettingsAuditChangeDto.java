package io.clueterra.api.admin.settings.dto;

public record SettingsAuditChangeDto(
        String fieldName,
        String oldValue,
        String newValue) {
}
