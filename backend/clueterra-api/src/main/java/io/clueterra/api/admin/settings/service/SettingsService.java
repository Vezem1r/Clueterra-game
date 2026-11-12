package io.clueterra.api.admin.settings.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.admin.settings.dto.SettingsAuditChangeDto;
import io.clueterra.api.admin.settings.dto.SettingsAuditEntryDto;
import io.clueterra.api.common.dto.PageResponse;
import io.clueterra.api.admin.settings.model.AppSetting;
import io.clueterra.api.admin.settings.repository.SettingsAuditRow;
import io.clueterra.api.admin.settings.repository.SettingsJdbcRepository;
import io.clueterra.api.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private final SettingsJdbcRepository settingsRepository;

    public Object getSetting(AppSetting setting) {
        return settingsRepository.getSetting(setting.getDbKey())
                .map(setting::parse)
                .orElse(setting.parse(setting.getDefaultValue()));
    }

    public void setSetting(AppSetting setting, Object value) {
        settingsRepository.setSetting(setting.getDbKey(), String.valueOf(value));
    }

    public boolean isRegistrationEnabled() {
        return (boolean) getSetting(AppSetting.REGISTRATION_ENABLED);
    }

    @Transactional
    public void setRegistrationEnabled(boolean enabled, UserPrincipal principal) {
        updateSettingIfChanged(AppSetting.REGISTRATION_ENABLED, enabled, principal, UUID.randomUUID());
    }

    public int getHintsPerCountry() {
        return (int) getSetting(AppSetting.HINTS_PER_COUNTRY);
    }

    public int getBaseXp(String level) {
        return (int) getSetting(AppSetting.valueOf("BASE_XP_" + level.toUpperCase()));
    }

    public double getHintsBonusMultiplier() {
        return (double) getSetting(AppSetting.HINTS_BONUS_MULTIPLIER);
    }

    public double getAttemptsBonusMultiplier() {
        return (double) getSetting(AppSetting.ATTEMPTS_BONUS_MULTIPLIER);
    }

    public double getParticipationMultiplier() {
        return (double) getSetting(AppSetting.PARTICIPATION_MULTIPLIER);
    }

    public int getLevelDivisor() {
        return (int) getSetting(AppSetting.LEVEL_DIVISOR);
    }

    public int getMaxAttempts() {
        return (int) getSetting(AppSetting.MAX_ATTEMPTS);
    }

    public double getTimeBonusMultiplier() {
        return (double) getSetting(AppSetting.TIME_BONUS_MULTIPLIER);
    }

    public int getTimeBonusThreshold() {
        return (int) getSetting(AppSetting.TIME_BONUS_THRESHOLD);
    }

    public int getStreakMaxBonusPercent() {
        return (int) getSetting(AppSetting.STREAK_MAX_BONUS_PERCENT);
    }

    public int getStreakMaxDays() {
        return (int) getSetting(AppSetting.STREAK_MAX_DAYS);
    }

    public double getStreakCurvePower() {
        return (double) getSetting(AppSetting.STREAK_CURVE_POWER);
    }

    public int getStreakFreezeMax() {
        return (int) getSetting(AppSetting.STREAK_FREEZE_MAX);
    }

    public int getStreakFreezeStreakInterval() {
        return (int) getSetting(AppSetting.STREAK_FREEZE_STREAK_INTERVAL);
    }

    public int getStreakFreezePerBadge() {
        return (int) getSetting(AppSetting.STREAK_FREEZE_PER_BADGE);
    }

    public int getBudgetBaseXpPerQuestion() {
        return (int) getSetting(AppSetting.BUDGET_BASE_XP_PER_QUESTION);
    }

    public int getBudgetFullXpThresholdPercent() {
        return (int) getSetting(AppSetting.BUDGET_FULL_XP_THRESHOLD_PERCENT);
    }

    public double getBudgetXpCurvePower() {
        return (double) getSetting(AppSetting.BUDGET_XP_CURVE_POWER);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        for (AppSetting setting : AppSetting.values()) {
            settings.put(setting.getApiFieldName(), getSetting(setting));
        }
        return settings;
    }

    @Transactional
    public Map<String, Object> updateSettings(Map<String, Object> updates, UserPrincipal principal) {
        boolean levelDivisorChanged = false;
        UUID batchId = UUID.randomUUID();

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            AppSetting setting = AppSetting.fromApiFieldName(key);
            if (setting != null) {
                boolean changed = updateSettingIfChanged(setting, value, principal, batchId);
                if (changed && setting == AppSetting.LEVEL_DIVISOR) {
                    levelDivisorChanged = true;
                }
            }
        }

        if (levelDivisorChanged) {
            log.info("Experience settings has been changed by Admin, recalculating user levels");
            recalculateAllLevels();
        }

        return getAllSettings();
    }

    @Transactional(readOnly = true)
    public PageResponse<SettingsAuditEntryDto> getRecentAudit(int page, int size) {
        int offset = page * size;
        long totalElements = settingsRepository.countAuditBatches();
        List<SettingsAuditRow> rows = settingsRepository.findRecentAudit(size, offset);
        Map<UUID, SettingsAuditRow> batchHeaders = new LinkedHashMap<>();
        Map<UUID, List<SettingsAuditChangeDto>> batchChanges = new LinkedHashMap<>();

        for (SettingsAuditRow row : rows) {
            batchHeaders.putIfAbsent(row.changeBatchId(), row);
            batchChanges.computeIfAbsent(row.changeBatchId(), ignored -> new ArrayList<>())
                    .add(new SettingsAuditChangeDto(resolveFieldName(row.settingKey()), row.oldValue(), row.newValue()));
        }

        List<SettingsAuditEntryDto> content = batchHeaders.values().stream()
                .map(row -> new SettingsAuditEntryDto(
                        row.changeBatchId().toString(),
                        row.changedAt(),
                        row.changedByUserId(),
                        row.changedByEmail(),
                        row.changedByUsername(),
                        batchChanges.getOrDefault(row.changeBatchId(), List.of())))
                .toList();

        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, totalElements, totalPages, page, size);
    }

    public void recalculateAllLevels() {
        int divisor = getLevelDivisor();
        settingsRepository.recalculateAllLevels(divisor);
    }

    private boolean updateSettingIfChanged(AppSetting setting, Object rawValue, UserPrincipal principal, UUID batchId) {
        Object oldValue = getSetting(setting);
        Object newValue = setting.parse(String.valueOf(rawValue));
        validateSetting(setting, newValue);

        if (oldValue.equals(newValue)) {
            return false;
        }

        log.info("Setting '{}' changed from {} to {}", setting.getApiFieldName(), oldValue, newValue);
        setSetting(setting, newValue);
        settingsRepository.insertAuditRow(
                batchId,
                setting.getDbKey(),
                String.valueOf(oldValue),
                String.valueOf(newValue),
                principal != null ? principal.getId() : null,
                principal != null ? principal.getEmail() : null,
                principal != null ? principal.getDisplayUsername() : null);
        return true;
    }

    private void validateSetting(AppSetting setting, Object value) {
        if (setting == AppSetting.BUDGET_BASE_XP_PER_QUESTION) {
            int baseXp = ((Number) value).intValue();
            if (baseXp < 1 || baseXp > 10_000) {
                throw new IllegalArgumentException("Budget XP per question must be between 1 and 10000");
            }
        } else if (setting == AppSetting.BUDGET_FULL_XP_THRESHOLD_PERCENT) {
            int thresholdPercent = ((Number) value).intValue();
            if (thresholdPercent < 0 || thresholdPercent > 99) {
                throw new IllegalArgumentException("Budget full XP threshold must be between 0 and 99 percent");
            }
        } else if (setting == AppSetting.BUDGET_XP_CURVE_POWER) {
            double curvePower = ((Number) value).doubleValue();
            if (!Double.isFinite(curvePower) || curvePower < 0.1 || curvePower > 3) {
                throw new IllegalArgumentException("Budget XP curve power must be between 0.1 and 3");
            }
        } else if (setting == AppSetting.RIVER_GAME_BASE_XP) {
            int baseXp = ((Number) value).intValue();
            if (baseXp < 1 || baseXp > 10_000) {
                throw new IllegalArgumentException("River XP per question must be between 1 and 10000");
            }
        } else if (setting == AppSetting.RIVER_GAME_HINT_PENALTY_PERCENT) {
            int penalty = ((Number) value).intValue();
            if (penalty < 0 || penalty > 33) {
                throw new IllegalArgumentException("River hint penalty must be between 0 and 33 percent");
            }
        } else if (setting == AppSetting.SILHOUETTE_GAME_BASE_XP) {
            int baseXp = ((Number) value).intValue();
            if (baseXp < 1 || baseXp > 10_000) {
                throw new IllegalArgumentException("Silhouette XP per country must be between 1 and 10000");
            }
        } else if (setting == AppSetting.SILHOUETTE_GAME_HINT_PENALTY_PERCENT) {
            int penalty = ((Number) value).intValue();
            if (penalty < 0 || penalty > 16) {
                throw new IllegalArgumentException("Silhouette clue penalty must be between 0 and 16 percent per unit");
            }
        } else if (setting == AppSetting.FLAG_GAME_BASE_XP) {
            int baseXp = ((Number) value).intValue();
            if (baseXp < 1 || baseXp > 10_000) {
                throw new IllegalArgumentException("Flag XP per country must be between 1 and 10000");
            }
        } else if (setting == AppSetting.FLAG_GAME_HINT_PENALTY_PERCENT) {
            int penalty = ((Number) value).intValue();
            if (penalty < 0 || penalty > 25) {
                throw new IllegalArgumentException("Flag clue penalty must be between 0 and 25 percent per unit");
            }
        } else if (setting == AppSetting.QUIZ_GAME_ANSWER_XP) {
            int answerXp = ((Number) value).intValue();
            if (answerXp < 1 || answerXp > 5_000) {
                throw new IllegalArgumentException("World quiz XP per correct answer must be between 1 and 5000");
            }
        }
    }

    private String resolveFieldName(String settingKey) {
        AppSetting setting = AppSetting.fromDbKey(settingKey);
        return setting != null ? setting.getApiFieldName() : settingKey;
    }
}
