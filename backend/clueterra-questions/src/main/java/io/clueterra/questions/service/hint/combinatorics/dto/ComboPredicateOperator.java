package io.clueterra.questions.service.hint.combinatorics.dto;

import java.math.BigDecimal;

public enum ComboPredicateOperator {
    GT,
    GTE,
    LT,
    LTE;

    public boolean test(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return false;
        }

        int comparison = left.compareTo(right);
        return switch (this) {
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
        };
    }
}
