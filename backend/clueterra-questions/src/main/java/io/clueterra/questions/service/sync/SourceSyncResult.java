package io.clueterra.questions.service.sync;

import io.clueterra.questions.clients.common.dto.FetchFailure;
import io.clueterra.questions.entity.enums.DataSource;

import java.util.List;

public record SourceSyncResult(
        DataSource source,
        int countriesProcessed,
        int statisticsSaved,
        int hintsGenerated,
        int staleStatisticsDeleted,
        int enrichedStatistics,
        List<FetchFailure> failures
) {
    public SourceSyncResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public SourceSyncResult(DataSource source,
                            int countriesProcessed,
                            int statisticsSaved,
                            int hintsGenerated,
                            int staleStatisticsDeleted,
                            int enrichedStatistics) {
        this(source, countriesProcessed, statisticsSaved, hintsGenerated, staleStatisticsDeleted,
                enrichedStatistics, List.of());
    }

    public static SourceSyncResult empty(DataSource source) {
        return empty(source, List.of());
    }

    public static SourceSyncResult empty(DataSource source, List<FetchFailure> failures) {
        return new SourceSyncResult(source, 0, 0, 0, 0, 0, failures);
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}
