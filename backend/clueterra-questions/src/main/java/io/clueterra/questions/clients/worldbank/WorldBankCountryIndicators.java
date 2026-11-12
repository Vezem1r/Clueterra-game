package io.clueterra.questions.clients.worldbank;

import io.clueterra.questions.clients.common.dto.RawStatistic;

import java.util.List;

public record WorldBankCountryIndicators(List<RawStatistic> statistics, List<String> failedIndicators) {

    public WorldBankCountryIndicators {
        statistics = List.copyOf(statistics);
        failedIndicators = List.copyOf(failedIndicators);
    }

    public boolean complete() {
        return failedIndicators.isEmpty();
    }
}
