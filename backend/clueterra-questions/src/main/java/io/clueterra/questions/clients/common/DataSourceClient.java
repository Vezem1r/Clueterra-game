package io.clueterra.questions.clients.common;

import io.clueterra.questions.clients.common.dto.FetchResult;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.entity.enums.DataSource;

import java.util.List;
import java.util.Optional;

public interface DataSourceClient {

    DataSource getSource();

    default int getPriority() {
        return getSource().getPriority();
    }

    default List<RawCountryData> fetchAllCountries() {
        return fetchAllCountries(FetchProgressListener.NO_OP);
    }

    List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener);

    Optional<RawCountryData> fetchCountryByCode(String countryCode);

    boolean isAvailable();

    String getBaseUrl();

    default boolean isDynamic() {
        return false;
    }

    default FetchResult getLastFetchResult() {
        return null;
    }
}
