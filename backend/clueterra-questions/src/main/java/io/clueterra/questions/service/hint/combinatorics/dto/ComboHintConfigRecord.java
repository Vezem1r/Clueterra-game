package io.clueterra.questions.service.hint.combinatorics.dto;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.HintType;

import java.math.BigDecimal;

public record ComboHintConfigRecord(
        String indicatorCode,
        String displayName,
        Category category,
        HintType hintType,
        String familyType,
        int baseDifficulty,
        BigDecimal baseQuality,
        Integer maxAmbiguityCount
) {
}
