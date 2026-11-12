package io.clueterra.api.admin.settings.repository;

import java.time.Instant;
import java.util.UUID;

public record SettingsAuditRow(
        long id,
        UUID changeBatchId,
        String settingKey,
        String oldValue,
        String newValue,
        Instant changedAt,
        Long changedByUserId,
        String changedByEmail,
        String changedByUsername) {
}
