package io.clueterra.questions.clients.common;

import java.util.List;

public interface StagingRepository<R extends CountryRow> {
    void upsertBatch(List<R> rows);

    List<R> findLastFetched();

    List<R> findLastFetchedByCountryCode(String countryCode);

    void markAllAsNotFetched();
}
