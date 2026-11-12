package io.clueterra.questions.entity.enums;

import lombok.Getter;

@Getter
public enum HintBand {
    EASY_HINT(1, 40),
    MEDIUM_HINT(41, 70),
    HARD_HINT(71, 100);

    private final int minDifficulty;
    private final int maxDifficulty;

    HintBand(int minDifficulty, int maxDifficulty) {
        this.minDifficulty = minDifficulty;
        this.maxDifficulty = maxDifficulty;
    }

    public boolean contains(int difficulty) {
        return difficulty >= minDifficulty && difficulty <= maxDifficulty;
    }

    public static HintBand fromDifficulty(int difficulty) {
        if (difficulty <= 40) return EASY_HINT;
        if (difficulty <= 70) return MEDIUM_HINT;
        return HARD_HINT;
    }
}
