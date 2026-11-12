package io.clueterra.questions.entity.enums;

import lombok.Getter;

@Getter
public enum Difficulty {
    EASY(1, 20, "Easy"),
    MEDIUM(21, 40, "Medium"),
    MEDIUM_HARD(41, 60, "Medium-Hard"),
    HARD(61, 80, "Hard"),
    EXPERT(81, 100, "Expert");

    private final int minScore;
    private final int maxScore;
    private final String displayName;

    Difficulty(int minScore, int maxScore, String displayName) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.displayName = displayName;
    }

    public boolean contains(int score) {
        return score >= minScore && score <= maxScore;
    }
}
