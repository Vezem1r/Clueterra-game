package io.clueterra.questions.clients.wikidata;

import io.clueterra.questions.clients.common.CountryReference;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.wikidata.cinema.WikidataCinemaFact;
import io.clueterra.questions.clients.wikidata.cinema.WikidataCinemaService;
import io.clueterra.questions.clients.wikidata.dto.WikidataMetric;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WikidataFetcher {

    private static final BigDecimal CONFIDENCE = BigDecimal.valueOf(0.95);
    private static final BigDecimal WEIGHT = BigDecimal.valueOf(0.86);

    private final WikidataSparqlService sparqlService;
    private final WikidataStatementService statementService;
    private final WikidataCinemaService cinemaService;

    public List<RawCountryData> fetchAllCountries(String restBaseUrl,
                                                  String queryUrl,
                                                  String actionApiUrl,
                                                  Duration timeout,
                                                  FetchProgressListener listener) {
        Map<String, String> qidByCountryCode = sparqlService.getAllCountryQids(queryUrl, timeout);
        Map<String, List<WikidataCinemaFact>> cinemaByCountry = cinemaService.fetchAll(
                queryUrl, actionApiUrl, qidByCountryCode, timeout);
        List<String> countryCodes = CountryReference.getAllCountryCodes().stream().sorted().toList();
        List<RawCountryData> results = new ArrayList<>();

        log.info("[Wikidata] Resolved {} ISO country identifiers; scanning {} supported countries",
                qidByCountryCode.size(), countryCodes.size());
        for (int index = 0; index < countryCodes.size(); index++) {
            String countryCode = countryCodes.get(index);
            String qid = qidByCountryCode.get(countryCode);
            if (StringUtils.hasText(qid)) {
                try {
                    List<RawStatistic> statistics = withCinema(
                            fetchStatsForCountry(qid, restBaseUrl, actionApiUrl, timeout),
                            cinemaByCountry.getOrDefault(countryCode, List.of()));
                    if (!statistics.isEmpty()) {
                        results.add(toCountryData(countryCode, statistics));
                    }
                } catch (Exception exception) {
                    log.warn("[Wikidata] Failed to fetch {} ({})", countryCode, qid, exception);
                }
            } else {
                log.debug("[Wikidata] No Wikidata item resolved for {}", countryCode);
            }

            int processed = index + 1;
            if (processed % 10 == 0 || processed == countryCodes.size()) {
                listener.onProgress(processed, countryCodes.size(), "Fetched Wikidata country facts");
                log.info("[Wikidata] Processed {}/{} countries", processed, countryCodes.size());
            }
        }
        return List.copyOf(results);
    }

    public List<RawStatistic> fetchStatsForCountry(String countryQid,
                                                   String restBaseUrl,
                                                   String actionApiUrl,
                                                   Duration timeout) {
        List<RawStatistic> statistics = new ArrayList<>();
        Map<WikidataMetric, List<String>> valuesByMetric = statementService.fetchAllValues(
                restBaseUrl, actionApiUrl, countryQid, timeout);
        for (WikidataMetric metric : WikidataMetric.values()) {
            toStatistic(metric, valuesByMetric.getOrDefault(metric, List.of())).ifPresent(statistics::add);
        }
        return List.copyOf(statistics);
    }

    public List<RawStatistic> fetchStatsForCountry(String countryCode,
                                                   String countryQid,
                                                   String restBaseUrl,
                                                   String queryUrl,
                                                   String actionApiUrl,
                                                   Duration timeout) {
        List<RawStatistic> countryFacts = fetchStatsForCountry(
                countryQid, restBaseUrl, actionApiUrl, timeout);
        List<WikidataCinemaFact> cinemaFacts = cinemaService.fetchCountry(
                countryCode, countryQid, queryUrl, actionApiUrl, timeout);
        return withCinema(countryFacts, cinemaFacts);
    }

    public String resolveQid(String countryCode, String queryUrl, Duration timeout) {
        return sparqlService.getCountryQid(queryUrl, countryCode, timeout);
    }

    private Optional<RawStatistic> toStatistic(WikidataMetric metric, List<String> rawValues) {
        String value = rawValues.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted(Comparator.comparing(candidate -> candidate.toLowerCase(Locale.ROOT)))
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        return Optional.of(new RawStatistic(
                metric.getCode(),
                metric.getLabel(),
                metric.getCategory(),
                null,
                value,
                null,
                null,
                CONFIDENCE,
                WEIGHT));
    }

    private List<RawStatistic> withCinema(List<RawStatistic> statistics,
                                          List<WikidataCinemaFact> cinemaFacts) {
        List<RawStatistic> combined = new ArrayList<>(statistics);
        combined.addAll(cinemaService.toStatistics(cinemaFacts));
        return List.copyOf(combined);
    }

    private RawCountryData toCountryData(String countryCode, List<RawStatistic> statistics) {
        return RawCountryData.builder()
                .isoAlpha2(countryCode)
                .nameEn(Optional.ofNullable(CountryReference.getCountryName(countryCode)).orElse(countryCode))
                .source(DataSource.WIKIDATA)
                .statistics(statistics)
                .build();
    }
}
