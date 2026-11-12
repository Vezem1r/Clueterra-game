package io.clueterra.questions.service.hint.combinatorics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CombinatoricsHintDifficultyCalculatorTest {

    private final CombinatoricsHintDifficultyCalculator calculator = new CombinatoricsHintDifficultyCalculator();

    @Test
    void computeDifficulty_reducesStartingDifficultyForMultiStatCombo() {
        int difficulty = calculator.computeDifficulty(63, 1, 2, "CONTRAST");

        assertThat(difficulty).isEqualTo(61);
    }

    @Test
    void computeDifficulty_increasesWithAmbiguityButNoLongerSaturatesImmediately() {
        int lowAmbiguity = calculator.computeDifficulty(57, 1, 2, "PROFILE");
        int mediumAmbiguity = calculator.computeDifficulty(57, 4, 2, "PROFILE");
        int highAmbiguity = calculator.computeDifficulty(57, 30, 2, "PROFILE");

        assertThat(lowAmbiguity).isEqualTo(53);
        assertThat(mediumAmbiguity).isGreaterThan(lowAmbiguity);
        assertThat(highAmbiguity).isGreaterThan(mediumAmbiguity);
        assertThat(highAmbiguity).isLessThan(95);
    }
}
