package io.clueterra.questions.service.source;

import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SourceStatisticProcessor {

    Optional<Country> mergeCountryData(List<RawCountryData> rawDataList);

    List<CountryStatistic> buildStatisticsSnapshot(Long countryId, List<RawCountryData> rawDataList);

    List<CountryStatistic> buildDynamicStatistics(Long countryId,
                                                  DataSource source,
                                                  Instant fetchedAt,
                                                  List<RawStatistic> rawStatistics);
}
