package io.clueterra.questions.service.hint;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

/**
 * Shared calculator for dynamic hint difficulty and quality, based on ambiguity count.
 * Difficulty formula: baseDifficulty + min(40, ambiguity * 3), capped at 95.
 * Quality formula: baseQuality * uniquenessFactor [* confidenceFactor].
 */
@Component
public class DynamicDifficultyCalculator {

    public int computeDifficulty(String indicatorCode, int ambiguity, int defaultBaseDifficulty) {
        int baseDifficulty = IndicatorConfig.getBaseDifficulty(indicatorCode, defaultBaseDifficulty);
        return HintScoreSupport.applyLinearAmbiguityBoost(baseDifficulty, ambiguity, 3, 40, 95);
    }

    public BigDecimal computeQuality(String indicatorCode, int ambiguity, BigDecimal defaultQuality) {
        return computeQuality(indicatorCode, ambiguity, defaultQuality, 1.0);
    }

    public BigDecimal computeQuality(String indicatorCode, int ambiguity,
                                     BigDecimal defaultQuality, double confidence) {
        BigDecimal baseQuality = IndicatorConfig.getQualityScore(indicatorCode, defaultQuality);
        return HintScoreSupport.applyLinearUniquenessPenalty(baseQuality, ambiguity, 0.05, 0.50, confidence);
    }
}
