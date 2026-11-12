package io.clueterra.questions.service.hint.combinatorics.dto;

import java.math.BigDecimal;

public record ComboHintPredicate(
        String indicatorCode,
        int predicateOrder,
        String leftComponentKey,
        ComboPredicateOperator operator,
        ComboPredicateMode comparisonMode,
        BigDecimal rightValueNumeric,
        String rightComponentKey
) {
}
