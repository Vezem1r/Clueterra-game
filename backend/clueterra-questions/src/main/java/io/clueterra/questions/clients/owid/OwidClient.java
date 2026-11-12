package io.clueterra.questions.clients.owid;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.clueterra.questions.clients.common.AbstractStaticClient;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.FetchFailure;
import io.clueterra.questions.clients.common.dto.FetchResult;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.owid.dto.OwidDataset;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OwidClient extends AbstractStaticClient {

    private final HttpClientHelper httpClient;
    private final SourcesProperties sourcesProperties;
    private final OwidParser parser;
    private final OwidMetadataValidator metadataValidator;

    private static final String CACHE_KEY = "all";

    private Cache<String, List<RawCountryData>> dataCache;

    @PostConstruct
    public void init() {
        initBase(httpClient, sourcesProperties);
        if (isDisabled()) return;

        int cacheDays = props.getCacheDurationDays() != null ? props.getCacheDurationDays() : 7;
        this.dataCache = Caffeine.newBuilder().expireAfterWrite(cacheDays, TimeUnit.DAYS).build();

        log.info("[OWID] Initialized: {} datasets, cache {}d, rate {}/s, timeout {}s",
                OwidDataset.values().length, cacheDays,
                props.getRateLimitPerSecond(), props.getTimeoutSeconds());
    }

    @Override
    public DataSource getSource() {
        return DataSource.OUR_WORLD_IN_DATA;
    }

    @Override
    public String getBaseUrl() {
        return "https://ourworldindata.org";
    }

    @Override
    public boolean isAvailable() {
        if (isDisabled()) return false;
        return httpClient.healthCheck(getSource(),
                "https://ourworldindata.org/grapher/happiness-cantril-ladder.csv", timeout);
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (isDisabled()) return List.of();
        List<RawCountryData> cached = dataCache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            lastFetchResult.set(new FetchResult(cached.size(), List.of()));
            return cached;
        }
        List<RawCountryData> fetched = fetchAll(progressListener);
        if (fetched.stream().allMatch(RawCountryData::isStatisticsComplete)) {
            dataCache.put(CACHE_KEY, fetched);
        }
        return fetched;
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (isDisabled()) return Optional.empty();
        return fetchAllCountries(null).stream()
                .filter(d -> countryCode.equalsIgnoreCase(d.getIsoAlpha2()))
                .findFirst();
    }

    private List<RawCountryData> fetchAll(FetchProgressListener progressListener) {
        Map<String, List<RawStatistic>> statsByIso2 = new LinkedHashMap<>();
        List<FetchFailure> failures = new ArrayList<>();
        OwidDataset[] datasets = OwidDataset.values();

        for (int i = 0; i < datasets.length; i++) {
            OwidDataset dataset = datasets[i];
            try {
                if (!metadataValidator.isAllowed(dataset, timeout)) {
                    log.warn("[OWID] Skipping legally unverified dataset {}", dataset.name());
                } else {
                    Optional<String> csv = httpClient.getOptional(getSource(), dataset.csvUrl(), timeout);
                    if (csv.isEmpty()) {
                        throw new IllegalStateException("Dataset CSV not found (HTTP 404)");
                    }
                    List<RawCountryData> rows = parser.parseGrapherCsv(csv.get(), dataset);
                    for (RawCountryData row : rows) {
                        statsByIso2.computeIfAbsent(row.getIsoAlpha2(), k -> new ArrayList<>())
                                .addAll(row.getStatistics());
                    }
                    log.info("[OWID] {} -> {} countries", dataset.indicatorCode(), rows.size());
                }
            } catch (Exception e) {
                log.warn("[OWID] Failed to fetch dataset {}", dataset.name(), e);
                failures.add(new FetchFailure(null, dataset.indicatorCode(), e.getMessage()));
            }
            if (progressListener != null) progressListener.onProgress(i + 1, datasets.length, "owid");
        }

        boolean complete = failures.isEmpty();
        List<RawCountryData> result = new ArrayList<>();
        for (Map.Entry<String, List<RawStatistic>> entry : statsByIso2.entrySet()) {
            result.add(RawCountryData.builder()
                    .isoAlpha2(entry.getKey())
                    .nameEn(entry.getKey())
                    .source(DataSource.OUR_WORLD_IN_DATA)
                    .statistics(entry.getValue())
                    .statisticsComplete(complete)
                    .build());
        }
        lastFetchResult.set(new FetchResult(result.size(), failures));
        log.info("[OWID] Total: {} countries across {} datasets, {} datasets failed",
                result.size(), datasets.length, failures.size());
        return result;
    }
}
