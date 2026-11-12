package io.clueterra.questions.clients.bigmac;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class BigMacClient implements DataSourceClient {

    private static final String CSV_PATH = "/big-mac-full-index.csv";

    private final HttpClientHelper httpClient;
    private final SourcesProperties sourcesProperties;
    private final BigMacParser parser;

    private SourceProperties props;
    private String normalizedBaseUrl;
    private Duration timeout;
    private Cache<String, List<RawCountryData>> csvCache;

    @PostConstruct
    public void init() {
        this.props = sourcesProperties.getSource(getSource());

        if (!props.isEnabled()) {
            log.info("[{}] Client is disabled", getSource());
            return;
        }

        this.normalizedBaseUrl = httpClient.normalizeBaseUrl(props.getBaseUrl());
        this.timeout = Duration.ofSeconds(props.getTimeoutSeconds());
        httpClient.configureRateLimiter(getSource(), props.getRateLimitPerSecond());

        int cacheDays = props.getCacheDurationDays() != null ? props.getCacheDurationDays() : 1;
        this.csvCache = Caffeine.newBuilder().expireAfterWrite(cacheDays, TimeUnit.DAYS).build();

        log.info("=== Big Mac Index Client Initialization ===");
        log.info("[{}] Base URL: {}", getSource(), normalizedBaseUrl);
        log.info("[{}] Rate limit: {} req/sec", getSource(), props.getRateLimitPerSecond());
        log.info("[{}] Timeout: {}s", getSource(), props.getTimeoutSeconds());
        log.info("[{}] Cache: {} day(s)", getSource(), cacheDays);
        log.info("[{}] Enabled: {}", getSource(), props.isEnabled());
        log.info("==========================================");
    }

    @Override
    public DataSource getSource() {
        return DataSource.BIG_MAC_INDEX;
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
        String healthUrl = httpClient.buildUrl(normalizedBaseUrl, CSV_PATH);
        return httpClient.healthCheck(getSource(), healthUrl, timeout);
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (!props.isEnabled()) {
            log.debug("[{}] Client is disabled, skipping fetch", getSource());
            return List.of();
        }
        try {
            return getCachedData();
        } catch (Exception e) {
            log.warn("[{}] Failed to fetch Big Mac Index CSV", getSource(), e);
            return List.of();
        }
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (!props.isEnabled()) {
            return Optional.empty();
        }
        String iso2 = countryCode.toUpperCase();
        try {
            return getCachedData().stream()
                    .filter(d -> iso2.equals(d.getIsoAlpha2()))
                    .findFirst();
        } catch (Exception e) {
            log.warn("[{}] Failed to fetch Big Mac data for {}", getSource(), iso2, e);
            return Optional.empty();
        }
    }

    private List<RawCountryData> getCachedData() {
        return csvCache.get("all", key -> {
            String url = httpClient.buildUrl(normalizedBaseUrl, CSV_PATH);
            return httpClient.getOptional(getSource(), url, timeout)
                    .map(parser::parseCsv)
                    .orElse(List.of());
        });
    }
}
