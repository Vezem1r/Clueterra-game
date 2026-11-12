package io.clueterra.questions.service.sync;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.service.enrichment.dto.IndicatorKey;

import java.util.Set;

public record SlicePersistenceResult(
        Country country,
        int statisticsSaved,
        int staleStatisticsDeleted,
        Set<IndicatorKey> comparableKeys,
        Set<String> changedIndicatorCodes
) {
    public static SlicePersistenceResult empty() {
        return new SlicePersistenceResult(null, 0, 0, Set.of(), Set.of());
    }
}
