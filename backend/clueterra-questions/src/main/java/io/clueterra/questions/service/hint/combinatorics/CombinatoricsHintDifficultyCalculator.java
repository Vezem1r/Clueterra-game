package io.clueterra.questions.service.hint.combinatorics;

import io.clueterra.questions.service.hint.HintScoreSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class CombinatoricsHintDifficultyCalculator {

    private static final BigDecimal MIN_QUALITY = new BigDecimal("0.35");
    private static final BigDecimal MAX_QUALITY = new BigDecimal("0.98");

    public int computeDifficulty(int baseDifficulty, int ambiguityCount, int componentCount, String familyType) {
        int familyAdjustment = switch (familyType != null ? familyType : "PROFILE") {
            case "CONTRAST" -> 0;
            case "CLUSTER" -> -1;
            default -> -2;
        };
        int specificityDiscount = Math.min(6, Math.max(0, componentCount - 1) * 2);
        int adjustedBase = Math.max(8, baseDifficulty + familyAdjustment - specificityDiscount);
        return HintScoreSupport.applyLogAmbiguityBoost(
                adjustedBase,
                Math.max(1, ambiguityCount),
                3.5,
                95
        );
    }

    public BigDecimal computeQuality(BigDecimal baseQuality, int ambiguityCount, int componentCount) {
        BigDecimal normalizedBase = baseQuality != null ? baseQuality : BigDecimal.valueOf(0.80);
        BigDecimal uniquenessAdjusted = HintScoreSupport.applyLinearUniquenessPenalty(
                normalizedBase,
                ambiguityCount,
                0.04,
                0.45,
                1.0
        );
        double componentBonus = 1.0 + Math.min(0.10, Math.max(0, componentCount - 2) * 0.05);
        BigDecimal adjusted = uniquenessAdjusted
                .multiply(BigDecimal.valueOf(componentBonus))
                .setScale(2, RoundingMode.HALF_UP);
        if (adjusted.compareTo(MIN_QUALITY) < 0) {
            return MIN_QUALITY;
        }
        if (adjusted.compareTo(MAX_QUALITY) > 0) {
            return MAX_QUALITY;
        }
        return adjusted;
    }
}
