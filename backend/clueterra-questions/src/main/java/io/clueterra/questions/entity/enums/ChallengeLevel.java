package io.clueterra.questions.entity.enums;

import lombok.Getter;

@Getter
public enum ChallengeLevel {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard");

    private final String displayName;

    ChallengeLevel(String displayName) {
        this.displayName = displayName;
    }

    public static ChallengeLevel fromString(String value) {
        if (value == null) return MEDIUM;
        return switch (value.toUpperCase()) {
            case "EASY" -> EASY;
            case "HARD" -> HARD;
            default -> MEDIUM;
        };
    }
}
