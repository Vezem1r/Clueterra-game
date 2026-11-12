package io.clueterra.questions.clients.common;

import io.clueterra.questions.clients.common.dto.RawStatistic;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractStagingEnricher<R extends CountryRow> {

    protected abstract StagingRepository<R> getRepository();

    protected abstract String logPrefix();

    protected abstract List<RawStatistic> enrichRows(String countryCode, List<R> rows);

    public Map<String, List<RawStatistic>> enrich() {
        List<R> allRows = getRepository().findLastFetched();
        if (allRows.isEmpty()) return Map.of();

        Map<String, List<R>> byCountry = allRows.stream()
                .collect(Collectors.groupingBy(CountryRow::countryCode));

        Map<String, List<RawStatistic>> result = new HashMap<>();
        for (Map.Entry<String, List<R>> entry : byCountry.entrySet()) {
            List<RawStatistic> stats = enrichRows(entry.getKey(), entry.getValue());
            if (!stats.isEmpty()) {
                result.put(entry.getKey(), stats);
            }
        }

        log.info("[{}] Enrichment completed: {} countries with stats", logPrefix(), result.size());
        return result;
    }

    public Map<String, List<RawStatistic>> enrichCountry(String countryCode) {
        List<R> rows = getRepository().findLastFetchedByCountryCode(countryCode);
        if (rows.isEmpty()) return Map.of();

        List<RawStatistic> stats = enrichRows(countryCode, rows);
        if (stats.isEmpty()) return Map.of();
        return Map.of(countryCode, stats);
    }
}
