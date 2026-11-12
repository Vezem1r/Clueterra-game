package io.clueterra.questions.service.hint.combinatorics.dto;

import io.clueterra.questions.entity.enums.DataSource;

public record ComboHintComponent(
        String indicatorCode,
        String componentKey,
        DataSource statSource,
        String statIndicatorCode,
        ComboValueFormatter formatter,
        boolean required
) {
    public String statKey() {
        return statSource.name() + "|" + statIndicatorCode;
    }
}
