package io.clueterra.questions.repository;

import java.math.BigDecimal;

public record HintCoverageStats(
        BigDecimal avgDifficulty,
        BigDecimal avgQualityScore,
        int categoriesCovered,
        int hintTypesCovered
) {
}
