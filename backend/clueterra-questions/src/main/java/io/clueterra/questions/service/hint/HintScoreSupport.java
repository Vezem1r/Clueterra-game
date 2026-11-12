package io.clueterra.questions.service.hint;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class HintScoreSupport {

    private HintScoreSupport() {
    }

    public static int applyLinearAmbiguityBoost(
            int baseDifficulty,
            int ambiguity,
            int perMatchBoost,
            int maxBoost,
            int maxDifficulty) {
        int normalizedAmbiguity = Math.max(0, ambiguity);
        int difficultyBoost = Math.min(maxBoost, normalizedAmbiguity * perMatchBoost);
        return Math.min(maxDifficulty, baseDifficulty + difficultyBoost);
    }

    public static BigDecimal applyLinearUniquenessPenalty(
            BigDecimal baseQuality,
            int ambiguity,
            double penaltyPerExtraMatch,
            double minimumQuality,
            double confidence) {
        int normalizedAmbiguity = Math.max(1, ambiguity);
        double uniqueness = Math.max(
                minimumQuality,
                1.0 - (normalizedAmbiguity - 1) * penaltyPerExtraMatch
        );
        return baseQuality
                .multiply(BigDecimal.valueOf(uniqueness))
                .multiply(BigDecimal.valueOf(Math.min(1.0, Math.max(0.0, confidence))))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static int applyLogAmbiguityBoost(
            int baseDifficulty,
            int ambiguity,
            double multiplier,
            int maxDifficulty) {
        if (ambiguity <= 1) {
            return baseDifficulty;
        }
        int bonus = (int) Math.round(multiplier * Math.log(ambiguity) / Math.log(2));
        return Math.min(maxDifficulty, baseDifficulty + bonus);
    }

    public static BigDecimal applyLogUniquenessPenalty(
            double baseQuality,
            int ambiguity,
            double penaltyMultiplier,
            double minimumQuality) {
        if (ambiguity <= 1) {
            return BigDecimal.valueOf(baseQuality).setScale(2, RoundingMode.HALF_UP);
        }
        double penalty = penaltyMultiplier * Math.log(ambiguity) / Math.log(2);
        double adjusted = Math.max(minimumQuality, baseQuality - penalty);
        return BigDecimal.valueOf(adjusted).setScale(2, RoundingMode.HALF_UP);
    }
}
