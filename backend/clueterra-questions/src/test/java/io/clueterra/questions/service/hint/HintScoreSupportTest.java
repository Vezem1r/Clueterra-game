package io.clueterra.questions.service.hint;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HintScoreSupportTest {

    @Test
    void applyLinearAmbiguityBoost_respectsBoostAndDifficultyCaps() {
        assertThat(HintScoreSupport.applyLinearAmbiguityBoost(30, 6, 2, 30, 95)).isEqualTo(42);
        assertThat(HintScoreSupport.applyLinearAmbiguityBoost(80, 40, 2, 30, 95)).isEqualTo(95);
    }

    @Test
    void applyLinearUniquenessPenalty_appliesPenaltyFloorAndConfidence() {
        assertThat(HintScoreSupport.applyLinearUniquenessPenalty(new BigDecimal("0.80"), 5, 0.05, 0.50, 0.5))
                .isEqualByComparingTo("0.32");
        assertThat(HintScoreSupport.applyLinearUniquenessPenalty(new BigDecimal("0.80"), 50, 0.05, 0.50, 1.0))
                .isEqualByComparingTo("0.40");
    }

    @Test
    void applyLogVariants_keepLowAmbiguityStableAndRespectFloor() {
        assertThat(HintScoreSupport.applyLogAmbiguityBoost(35, 1, 4.0, 95)).isEqualTo(35);
        assertThat(HintScoreSupport.applyLogUniquenessPenalty(0.72, 16, 0.03, 0.40))
                .isEqualByComparingTo("0.60");
    }
}
