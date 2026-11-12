package io.clueterra.questions.clients.curatedfacts;

import io.clueterra.questions.clients.common.CountryReference;
import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CuratedCountryFactsClient implements DataSourceClient {

    private static final BigDecimal CURATED_FACT_CONFIDENCE = BigDecimal.valueOf(0.82);

    private final CuratedCountryFactsLoader loader;
    private final SourcesProperties sourcesProperties;

    private final Map<CuratedCountryFactIndicator, Map<String, String>> factsByIndicator =
            new EnumMap<>(CuratedCountryFactIndicator.class);

    private SourceProperties properties;
    private List<RawCountryData> cachedCountryData = List.of();

    @PostConstruct
    public void init() {
        properties = sourcesProperties.getSource(getSource());
        if (!properties.isEnabled()) {
            log.info("[{}] Client is disabled", getSource());
            return;
        }

        for (CuratedCountryFactIndicator indicator : CuratedCountryFactIndicator.values()) {
            factsByIndicator.put(indicator, loader.load(indicator.getFileName()));
        }
        cachedCountryData = buildCountryData();
        log.info("[{}] Loaded {} countries and {} facts", getSource(), cachedCountryData.size(), countFacts());
    }

    @Override
    public DataSource getSource() {
        return DataSource.CURATED_COUNTRY_FACTS;
    }

    @Override
    public String getBaseUrl() {
        return properties != null ? properties.getBaseUrl() : null;
    }

    @Override
    public boolean isAvailable() {
        return properties != null && properties.isEnabled() && !cachedCountryData.isEmpty();
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (!isAvailable()) {
            return List.of();
        }

        FetchProgressListener listener = FetchProgressListener.safe(progressListener);
        int total = cachedCountryData.size();
        for (int index = 0; index < total; index++) {
            int current = index + 1;
            if (current % 25 == 0 || current == total) {
                listener.onProgress(current, total, "Loaded curated country facts");
            }
        }
        return cachedCountryData;
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (!isAvailable() || countryCode == null) {
            return Optional.empty();
        }
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        return cachedCountryData.stream()
                .filter(data -> normalized.equals(data.getIsoAlpha2()))
                .findFirst();
    }

    private List<RawCountryData> buildCountryData() {
        Map<String, List<RawStatistic>> statisticsByCountry = new TreeMap<>();
        factsByIndicator.forEach((indicator, facts) -> facts.forEach((countryCode, value) ->
                statisticsByCountry.computeIfAbsent(countryCode, ignored -> new ArrayList<>())
                        .add(toStatistic(indicator, value))));

        return statisticsByCountry.entrySet().stream()
                .map(entry -> RawCountryData.builder()
                        .isoAlpha2(entry.getKey())
                        .nameEn(CountryReference.getCountryName(entry.getKey()))
                        .source(getSource())
                        .statistics(entry.getValue())
                        .build())
                .toList();
    }

    private RawStatistic toStatistic(CuratedCountryFactIndicator indicator, String value) {
        return new RawStatistic(
                indicator.getCode(),
                indicator.getDisplayName(),
                indicator.getCategory(),
                null,
                value,
                null,
                null,
                CURATED_FACT_CONFIDENCE,
                indicator.getWeight());
    }

    private int countFacts() {
        return factsByIndicator.values().stream().mapToInt(Map::size).sum();
    }
}
