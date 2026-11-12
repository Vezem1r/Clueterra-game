package io.clueterra.questions.clients.wikidata;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.clients.common.CountryReference;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WikidataClient implements DataSourceClient {

    private final HttpClientHelper httpClient;
    private final WikidataFetcher fetcher;
    private final SourcesProperties sourcesProperties;

    private SourceProperties props;
    private String restBaseUrl;
    private String queryUrl;
    private String actionApiUrl;
    private Duration timeout;

    @PostConstruct
    public void init() {
        this.props = sourcesProperties.getSource(getSource());
        if (!props.isEnabled()) {
            log.info("[{}] Client is disabled", getSource());
            return;
        }

        this.restBaseUrl = httpClient.normalizeBaseUrl(props.getBaseUrl());
        this.queryUrl = httpClient.normalizeBaseUrl(props.getQueryUrl());
        this.actionApiUrl = httpClient.normalizeBaseUrl(props.getActionApiUrl());
        this.timeout = Duration.ofSeconds(props.getTimeoutSeconds());
        httpClient.configureRateLimiter(getSource(), props.getRateLimitPerSecond());

        log.info("=== Wikidata Client Initialization ===");
        log.info("REST URL: {}", restBaseUrl);
        log.info("SPARQL URL: {}", queryUrl);
        log.info("Rate Limit: {}/sec", props.getRateLimitPerSecond());
        log.info("[{}] Timeout: {}s", getSource(), props.getTimeoutSeconds());
        log.info("[{}] Enabled: {}", getSource(), props.isEnabled());
        log.info("=======================================");
    }

    @Override
    public DataSource getSource() {
        return DataSource.WIKIDATA;
    }

    @Override
    public String getBaseUrl() {
        return restBaseUrl;
    }

    @Override
    public boolean isAvailable() {
        if (!props.isEnabled()) {
            return false;
        }

        String url = restBaseUrl + "/entities/items/Q183/statements?property=P85";
        return httpClient.healthCheck(getSource(), url, timeout);
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (!props.isEnabled()) return List.of();

        FetchProgressListener listener = FetchProgressListener.safe(progressListener);

        log.info("[Wikidata] Starting bulk fetch for all countries");
        return fetcher.fetchAllCountries(restBaseUrl, queryUrl, actionApiUrl, timeout, listener);
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (!props.isEnabled()) return Optional.empty();

        String code = httpClient.normalizeCountryCode(countryCode);

        try {
            String qid = fetcher.resolveQid(code, queryUrl, timeout);
            if (!StringUtils.hasText(qid)) {
                log.warn("[Wikidata] Could not resolve QID for {}", code);
                return Optional.empty();
            }

            log.debug("[Wikidata] Fetching data for {} ({})", code, qid);

            List<RawStatistic> stats = fetcher.fetchStatsForCountry(
                    code, qid, restBaseUrl, queryUrl, actionApiUrl, timeout);

            if (stats.isEmpty()) return Optional.empty();
            String countryName = CountryReference.getCountryName(code);

            RawCountryData data = RawCountryData.builder()
                    .isoAlpha2(code)
                    .nameEn(countryName != null ? countryName : code)
                    .source(DataSource.WIKIDATA)
                    .statistics(stats)
                    .build();

            log.info("[Wikidata] Fetched {} facts for {}", stats.size(), code);
            return Optional.of(data);

        } catch (Exception e) {
            log.error("[Wikidata] Error fetching {}", code, e);
            return Optional.empty();
        }
    }
}
