package io.clueterra.api.common.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum BadgeColorPreset {
    VIOLET_DEEP("violet-deep"),
    VIOLET_CORE("violet-core"),
    VIOLET_GLOW("violet-glow"),
    INDIGO_DEEP("indigo-deep"),
    INDIGO_CORE("indigo-core"),
    INDIGO_GLOW("indigo-glow"),
    SKY_DEEP("sky-deep"),
    SKY_CORE("sky-core"),
    SKY_GLOW("sky-glow"),
    CYAN_DEEP("cyan-deep"),
    CYAN_CORE("cyan-core"),
    CYAN_GLOW("cyan-glow"),
    EMERALD_DEEP("emerald-deep"),
    EMERALD_CORE("emerald-core"),
    EMERALD_GLOW("emerald-glow"),
    AMBER_DEEP("amber-deep"),
    AMBER_CORE("amber-core"),
    AMBER_GLOW("amber-glow"),
    ROSE_DEEP("rose-deep"),
    ROSE_CORE("rose-core"),
    ROSE_GLOW("rose-glow");

    public static final String DEFAULT_ACCENT_KEY = "sky-core";
    public static final String DEFAULT_EFFECT_KEY = "amber-glow";

    private final String key;

    BadgeColorPreset(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static boolean isValidKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(preset -> preset.key.equals(normalized));
    }

    public static List<String> keys() {
        return Arrays.stream(values())
                .map(BadgeColorPreset::key)
                .toList();
    }
}
