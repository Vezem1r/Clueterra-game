package io.clueterra.api.admin.settings.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppSetting {
    REGISTRATION_ENABLED("registration_enabled", "false", Boolean.class),
    COUNTRIES_PER_CHALLENGE("countries_per_challenge", "5", Integer.class),
    HINTS_PER_COUNTRY("hints_per_country", "5", Integer.class),
    BASE_XP_EASY("base_xp_easy", "100", Integer.class),
    BASE_XP_MEDIUM("base_xp_medium", "200", Integer.class),
    BASE_XP_HARD("base_xp_hard", "400", Integer.class),
    HINTS_BONUS_MULTIPLIER("hints_bonus_multiplier", "0.3", Double.class),
    ATTEMPTS_BONUS_MULTIPLIER("attempts_bonus_multiplier", "0.2", Double.class),
    PARTICIPATION_MULTIPLIER("participation_multiplier", "0.1", Double.class),
    LEVEL_DIVISOR("level_divisor", "100", Integer.class),
    MAX_ATTEMPTS("max_attempts", "5", Integer.class),
    TIME_BONUS_MULTIPLIER("time_bonus_multiplier", "0.15", Double.class),
    TIME_BONUS_THRESHOLD("time_bonus_threshold", "30", Integer.class),
    AUTO_APPROVE_GENERATION("auto_approve_generation", "false", Boolean.class),
    AUTO_APPROVE_REVIEW("auto_approve_review", "false", Boolean.class),
    STREAK_MAX_BONUS_PERCENT("streak_max_bonus_percent", "50", Integer.class),
    STREAK_MAX_DAYS("streak_max_days", "365", Integer.class),
    STREAK_CURVE_POWER("streak_curve_power", "0.4", Double.class),
    STREAK_FREEZE_MAX("streak_freeze_max", "5", Integer.class),
    STREAK_FREEZE_STREAK_INTERVAL("streak_freeze_streak_interval", "10", Integer.class),
    STREAK_FREEZE_PER_BADGE("streak_freeze_per_badge", "1", Integer.class),
    BUDGET_BASE_XP_PER_QUESTION("budget_base_xp_per_question", "100", Integer.class),
    BUDGET_FULL_XP_THRESHOLD_PERCENT("budget_full_xp_threshold_percent", "20", Integer.class),
    BUDGET_XP_CURVE_POWER("budget_xp_curve_power", "0.75", Double.class),
    RIVER_GAME_BASE_XP("river_game_base_xp", "200", Integer.class),
    RIVER_GAME_HINT_PENALTY_PERCENT("river_game_hint_penalty_percent", "15", Integer.class),
    SILHOUETTE_GAME_BASE_XP("silhouette_game_base_xp", "150", Integer.class),
    SILHOUETTE_GAME_HINT_PENALTY_PERCENT("silhouette_game_hint_penalty_percent", "8", Integer.class),
    FLAG_GAME_BASE_XP("flag_game_base_xp", "150", Integer.class),
    FLAG_GAME_HINT_PENALTY_PERCENT("flag_game_hint_penalty_percent", "8", Integer.class),
    QUIZ_GAME_ANSWER_XP("quiz_game_answer_xp", "50", Integer.class);

    private final String dbKey;
    private final String defaultValue;
    private final Class<?> type;

    public Object parse(String value) {
        if (type == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (type == Integer.class) {
            return Integer.parseInt(value);
        } else if (type == Double.class) {
            return Double.parseDouble(value);
        }
        return value;
    }

    public String getApiFieldName() {
        String[] parts = name().split("_");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            sb.append(parts[i].substring(0, 1).toUpperCase())
                    .append(parts[i].substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public static AppSetting fromApiFieldName(String fieldName) {
        for (AppSetting setting : values()) {
            if (setting.getApiFieldName().equals(fieldName)) {
                return setting;
            }
        }
        return null;
    }

    public static AppSetting fromDbKey(String dbKey) {
        for (AppSetting setting : values()) {
            if (setting.getDbKey().equals(dbKey)) {
                return setting;
            }
        }
        return null;
    }
}
