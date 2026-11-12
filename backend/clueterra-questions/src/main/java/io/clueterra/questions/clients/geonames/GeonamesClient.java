package io.clueterra.questions.clients.geonames;

import io.clueterra.questions.clients.common.dto.RawCountryDataBuilder;
import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.DataSourceException;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeonamesClient implements DataSourceClient {

    private final HttpClientHelper httpClient;
    private final GeonamesFetcher fetcher;
    private final SourcesProperties sourcesProperties;

    private SourceProperties props;
    private String normalizedBaseUrl;
    private Duration timeout;
    private String username;

    @PostConstruct
    public void init() {
        this.props = sourcesProperties.getSource(getSource());

        if (!props.isEnabled()) {
            log.info("[{}] Client is disabled", getSource());
            return;
        }

        this.normalizedBaseUrl = httpClient.normalizeBaseUrl(props.getBaseUrl());
        this.timeout = Duration.ofSeconds(props.getTimeoutSeconds());
        this.username = props.getUsername();
        httpClient.configureRateLimiter(getSource(), props.getRateLimitPerSecond());

        log.info("=== GeoNames Client Initialization ===");
        log.info("[{}] Base URL: {}", getSource(), normalizedBaseUrl);
        log.info("[{}] Username: {}", getSource(), username);
        log.info("[{}] Rate limit: {} req/sec", getSource(), props.getRateLimitPerSecond());
        log.info("[{}] Timeout: {}s", getSource(), props.getTimeoutSeconds());
        log.info("[{}] Enabled: {}", getSource(), props.isEnabled());
        log.info("======================================");

        if (username == null || username.isBlank() || "demo".equals(username)) {
            log.warn("[{}] WARNING: Using demo username.", getSource());
        }
    }

    @Override
    public DataSource getSource() {
        return DataSource.GEONAMES;
    }

    @Override
    public String getBaseUrl() {
        return normalizedBaseUrl;
    }

    @Override
    public boolean isAvailable() {
        if (!props.isEnabled()) {
            log.debug("[{}] Client is disabled", getSource());
            return false;
        }
        return fetcher.healthCheck(normalizedBaseUrl, username, timeout);
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (!props.isEnabled()) {
            log.debug("[{}] Client is disabled, skipping fetch", getSource());
            return List.of();
        }

        log.info("[{}] === Starting fetch all countries ===", getSource());

        try {
            List<RawCountryData> basicCountries = fetcher.fetchAllBasicCountries(normalizedBaseUrl, username, timeout);
            log.info("[{}] Found {} countries, fetching data...", getSource(), basicCountries.size());

            List<RawCountryData> results = new ArrayList<>();
            int processed = 0;
            int successful = 0;

            for (RawCountryData basic : basicCountries) {
                try {
                    Optional<RawCountryData> comprehensive = fetchCountryByCode(basic.getIsoAlpha2());
                    if (comprehensive.isPresent()) {
                        results.add(comprehensive.get());
                        successful++;
                    } else {
                        results.add(basic);
                    }

                    processed++;
                    if (processed % 10 == 0) {
                        log.info("[{}] Progress: {}/{} countries processed", getSource(), processed, basicCountries.size());
                    }

                } catch (Exception e) {
                    log.warn("[{}] Failed fetch for {}, using basic data", getSource(), basic.getIsoAlpha2(), e);
                    results.add(basic);
                    processed++;
                }
            }
            log.info("[{}] === Fetch completed: {}/{} countries with full data ===", getSource(), successful, processed);
            return results;
        } catch (DataSourceException e) {
            log.error("[{}] === Fetch FAILED ===", getSource(), e);
            throw e;
        }
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (!props.isEnabled()) {
            return Optional.empty();
        }

        String normalizedCode = httpClient.normalizeCountryCode(countryCode);
        log.debug("[{}] Fetching data for {}", getSource(), normalizedCode);

        Optional<RawCountryData> basicDataOpt = fetcher.fetchBasicCountryByCode(normalizedCode, normalizedBaseUrl, username, timeout);
        if (basicDataOpt.isEmpty()) {
            log.debug("[{}] Country not found: {}", getSource(), normalizedCode);
            return Optional.empty();
        }

        RawCountryData basicData = basicDataOpt.get();

        RawCountryDataBuilder builder = RawCountryData.builder()
                .source(DataSource.GEONAMES)
                .isoAlpha2(basicData.getIsoAlpha2())
                .isoAlpha3(basicData.getIsoAlpha3())
                .nameEn(basicData.getNameEn())
                .capital(basicData.getCapital().orElse(null))
                .continents(basicData.getContinents())
                .languages(basicData.getLanguages())
                .currencies(basicData.getCurrencies());

        List<RawStatistic> allStats = new ArrayList<>(basicData.getStatistics());

        try {
            allStats.addAll(fetcher.fetchAllForCountry(normalizedCode, normalizedBaseUrl, username, timeout));
        } catch (Exception e) {
            log.debug("[{}] Could not fetch detailed stats for {}: {}", getSource(), normalizedCode, e.getMessage());
        }

        builder.statistics(allStats);

        RawCountryData enrichedData = builder.build();
        log.info("[{}] Fetched {} facts for {}", getSource(), allStats.size(), normalizedCode);

        return Optional.of(enrichedData);
    }
}
