package io.clueterra.api.admin.statistics.dto;

import java.math.BigDecimal;

public record UpdateStatisticRequest(
        BigDecimal value,
        String valueText,
        BigDecimal weight,
        Boolean isCurrent,
        BigDecimal confidence) {
}
