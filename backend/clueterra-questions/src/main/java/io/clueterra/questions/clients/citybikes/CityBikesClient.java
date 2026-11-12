package io.clueterra.questions.clients.citybikes;

import java.time.Clock;
import io.clueterra.questions.clients.citybikes.dto.CityBikesCityCluster;
import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkDetail;
import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkSummary;
import io.clueterra.questions.clients.common.AbstractStaticClient;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.CountryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.clueterra.questions.clients.citybikes.dto.CityBikesConstants.CONFIDENCE;
import static io.clueterra.questions.clients.citybikes.dto.CityBikesConstants.DEFAULT_DETAIL_LIMIT_PER_COUNTRY;
import static io.clueterra.questions.clients.citybikes.dto.CityBikesConstants.WEIGHT;

@Slf4j
@Component
@RequiredArgsConstructor
public class CityBikesClient extends AbstractStaticClient {

    private final HttpClientHelper httpClient;
    private final SourcesProperties sourcesProperties;
    private final CityBikesFetcher fetcher;
    private final CountryRepository countryRepository;
    private final Clock clock;

    @PostConstruct
    public void init() {
        initBase(httpClient, sourcesProperties);
        if (isDisabled()) {
            log.info("[{}] Client is disabled", getSource());
            return;
        }
        log.info("=== CityBikes Client Initialization ===");
        log.info("[{}] Base URL: {}", getSource(), normalizedBaseUrl);
        log.info("[{}] Rate limit: {} req/sec", getSource(), props.getRateLimitPerSecond());
        log.info("[{}] Timeout: {}s", getSource(), props.getTimeoutSeconds());
        log.info("[{}] Enabled: {}", getSource(), props.isEnabled());
        log.info("======================================");
    }

    @Override
    public DataSource getSource() {
        return DataSource.CITYBIKES;
    }

    @Override
    public boolean isAvailable() {
        if (isDisabled()) {
            return false;
        }
        return httpClient.healthCheck(getSource(), normalizedBaseUrl + "/networks?fields=id", timeout);
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (!isAvailable()) {
            return List.of();
        }

        List<CityBikesNetworkSummary> networks = fetcher.fetchNetworkIndex(normalizedBaseUrl, timeout);
        if (networks.isEmpty()) {
            return List.of();
        }

        Map<String, List<CityBikesNetworkSummary>> byCountry = networks.stream()
                .filter(network -> StringUtils.hasText(network.iso2()))
                .collect(Collectors.groupingBy(CityBikesNetworkSummary::iso2));
        Map<String, Country> activeCountries = countryRepository.findByIsActiveTrue().stream()
                .filter(country -> StringUtils.hasText(country.getIsoAlpha2()))
                .collect(Collectors.toMap(
                        country -> country.getIsoAlpha2().toUpperCase(Locale.ROOT),
                        country -> country,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<RawCountryData> result = new ArrayList<>();
        for (Map.Entry<String, List<CityBikesNetworkSummary>> entry : byCountry.entrySet()) {
            Country country = activeCountries.get(entry.getKey());
            if (country != null) {
                buildCountryData(country, entry.getValue()).ifPresent(result::add);
            }
        }

        log.info("[{}] Completed: {} countries fetched", getSource(), result.size());
        return result;
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (!isAvailable() || !StringUtils.hasText(countryCode)) {
            return Optional.empty();
        }

        Optional<Country> countryOpt = resolveCountry(countryCode);
        if (countryOpt.isEmpty()) {
            return Optional.empty();
        }

        String iso2 = countryOpt.get().getIsoAlpha2().toUpperCase(Locale.ROOT);
        List<CityBikesNetworkSummary> networks = fetcher.fetchNetworkIndex(normalizedBaseUrl, timeout).stream()
                .filter(network -> iso2.equals(network.iso2()))
                .toList();
        return buildCountryData(countryOpt.get(), networks);
    }

    private Optional<RawCountryData> buildCountryData(Country country, List<CityBikesNetworkSummary> networks) {
        if (country == null || networks == null || networks.isEmpty()) {
            return Optional.empty();
        }

        int year = Year.now(clock).getValue();
        int detailLimit = detailLimitPerCountry();
        List<CityBikesNetworkSummary> sorted = networks.stream()
                .sorted(Comparator.comparing(CityBikesNetworkSummary::city, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(CityBikesNetworkSummary::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        List<CityBikesNetworkDetail> details = sorted.stream()
                .limit(detailLimit)
                .map(summary -> fetcher.fetchNetworkDetail(normalizedBaseUrl, timeout, summary))
                .flatMap(Optional::stream)
                .toList();

        List<RawStatistic> stats = new ArrayList<>();
        Set<String> distinctCities = sorted.stream()
                .map(CityBikesNetworkSummary::city)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));

        stats.add(numericStat(
                BigDecimal.valueOf(sorted.size()), year));
        if (!distinctCities.isEmpty()) {
            stats.add(new RawStatistic(
                    "CITYBIKES_CITIES",
                    "CityBikes city count",
                    Category.INFRASTRUCTURE,
                    BigDecimal.valueOf(distinctCities.size()),
                    joinTopCities(distinctCities),
                    "cities",
                    year,
                    CONFIDENCE,
                    WEIGHT
            ));
        }

        findCityCluster(sorted).ifPresent(cluster -> stats.add(new RawStatistic(
                "CITYBIKES_CITY_CLUSTER",
                "CityBikes multi-network city",
                Category.INFRASTRUCTURE,
                BigDecimal.valueOf(cluster.networkCount()),
                cluster.city(),
                "networks",
                year,
                CONFIDENCE,
                WEIGHT
        )));

        if (!details.isEmpty()) {
            CityBikesNetworkDetail top = details.stream()
                    .max(Comparator.comparingInt(CityBikesNetworkDetail::stationCount)
                            .thenComparing(CityBikesNetworkDetail::freeBikes))
                    .orElse(details.getFirst());
            String subject = top.subject();

            if (top.stationCount() > 0) {
                stats.add(new RawStatistic(
                        "CITYBIKES_TOP_NETWORK",
                        "CityBikes top network",
                        Category.INFRASTRUCTURE,
                        BigDecimal.valueOf(top.stationCount()),
                        subject,
                        "stations",
                        year,
                        CONFIDENCE,
                        WEIGHT
                ));
            }
            if (top.ebikes() > 0) {
                stats.add(new RawStatistic(
                        "CITYBIKES_EBIKES",
                        "CityBikes e-bikes",
                        Category.INFRASTRUCTURE,
                        BigDecimal.valueOf(top.ebikes()),
                        subject,
                        "e-bikes",
                        year,
                        CONFIDENCE,
                        WEIGHT
                ));
            }
            if (StringUtils.hasText(top.company())) {
                stats.add(new RawStatistic(
                        "CITYBIKES_OPERATOR",
                        "CityBikes network operator",
                        Category.INFRASTRUCTURE,
                        null,
                        top.company(),
                        "operator",
                        year,
                        CONFIDENCE,
                        WEIGHT
                ));
            }
        }

        if (stats.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(RawCountryData.builder()
                .isoAlpha2(country.getIsoAlpha2())
                .isoAlpha3(country.getIsoAlpha3())
                .nameEn(country.getNameEn())
                .source(getSource())
                .statistics(stats)
                .build());
    }

    private Optional<Country> resolveCountry(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() == 2) {
            return countryRepository.findByIsoAlpha2(normalized);
        }
        if (normalized.length() == 3) {
            return countryRepository.findByIsoAlpha3(normalized);
        }
        return Optional.empty();
    }

    private Optional<CityBikesCityCluster> findCityCluster(List<CityBikesNetworkSummary> networks) {
        Map<String, Long> byCity = new HashMap<>();
        for (CityBikesNetworkSummary network : networks) {
            if (StringUtils.hasText(network.city())) {
                byCity.merge(network.city(), 1L, Long::sum);
            }
        }
        return byCity.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .max(Map.Entry.comparingByValue())
                .map(entry -> new CityBikesCityCluster(entry.getKey(), entry.getValue().intValue()));
    }

    private String joinTopCities(Set<String> cities) {
        return cities.stream()
                .sorted(String::compareToIgnoreCase)
                .limit(4)
                .collect(Collectors.joining(", "));
    }

    private RawStatistic numericStat(BigDecimal value, int year) {
        return new RawStatistic(
                "CITYBIKES_NETWORKS",
                "CityBikes network count",
                Category.INFRASTRUCTURE,
                value,
                null,
                "networks",
                year,
                CONFIDENCE,
                WEIGHT
        );
    }

    private int detailLimitPerCountry() {
        Integer configured = props != null ? props.getBatchSize() : null;
        if (configured == null) {
            return DEFAULT_DETAIL_LIMIT_PER_COUNTRY;
        }
        return Math.clamp(configured, 1, 10);
    }
}
