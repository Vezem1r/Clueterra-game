package io.clueterra.questions.service.hint;

import io.clueterra.questions.repository.IndicatorConfigRecord;
import java.util.Map;
import java.util.Set;

public record IndicatorConfigSnapshot(
        Set<String> excludeFromRanking,
        Set<String> excludeFromPercentile,
        Set<String> highProfileIndicators,
        Set<String> mediumProfileIndicators,
        Set<String> lowerIsBetterIndicators,
        Set<String> highInterestIndicators,
        Map<String, String> displayNames,
        Map<String, IndicatorConfigRecord> configByCode
) {
    static IndicatorConfigSnapshot empty() {
        return new IndicatorConfigSnapshot(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of(), Map.of()
        );
    }
}
