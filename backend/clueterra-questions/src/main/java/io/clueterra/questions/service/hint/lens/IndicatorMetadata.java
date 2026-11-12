package io.clueterra.questions.service.hint.lens;

import java.math.BigDecimal;

public record IndicatorMetadata(
        String code,
        String name,
        int countryCount,
        BigDecimal minValue,
        BigDecimal maxValue,
        BigDecimal avgValue
) {
}
