package io.clueterra.questions.clients.nagerdate;

import java.time.Clock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.common.AbstractStaticClient;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NagerDateClient extends AbstractStaticClient {

    private final HttpClientHelper httpClient;
    private final SourcesProperties sourcesProperties;
    private final ObjectMapper objectMapper;
    private final NagerDateParser parser;
    private final Clock clock;

    @PostConstruct
    public void init() {
        initBase(httpClient, sourcesProperties);
        if (isDisabled()) {
            log.info("[{}] Client is disabled", getSource());
            return;
        }
        log.info("=== Nager.Date Client Initialization ===");
        log.info("[{}] Base URL: {}", getSource(), normalizedBaseUrl);
        log.info("[{}] Rate limit: {} req/sec", getSource(), props.getRateLimitPerSecond());
        log.info("[{}] Timeout: {}s", getSource(), props.getTimeoutSeconds());
        log.info("[{}] Enabled: {}", getSource(), props.isEnabled());
        log.info("========================================");
    }

    @Override
    public DataSource getSource() {
        return DataSource.NAGER_DATE;
    }

    @Override
    public boolean isAvailable() {
        if (isDisabled()) return false;
        String healthUrl = httpClient.buildUrl(normalizedBaseUrl, "/AvailableCountries");
        return httpClient.healthCheck(getSource(), healthUrl, timeout);
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (!isAvailable()) return List.of();
        List<RawCountryData> result = new ArrayList<>();
        try {
            String url = httpClient.buildUrl(normalizedBaseUrl, "/AvailableCountries");
            Optional<String> body = httpClient.getOptional(getSource(), url, timeout);
            if (body.isEmpty()) {
                log.warn("[{}] Could not fetch available countries list", getSource());
                return result;
            }
            JsonNode arr = objectMapper.readTree(body.get());
            if (!arr.isArray()) return result;

            List<String> isoCodes = new ArrayList<>();
            for (JsonNode node : arr) {
                String code = node.path("countryCode").asText(null);
                if (code != null && !code.isBlank()) isoCodes.add(code.toUpperCase());
            }
            log.info("[{}] Fetching holidays for {} countries", getSource(), isoCodes.size());

            for (String iso2 : isoCodes) {
                fetchCountryByCode(iso2).ifPresent(result::add);
                httpClient.backoff(Duration.ofMillis(150));
            }
        } catch (Exception e) {
            log.warn("[{}] fetchAllCountries failed", getSource(), e);
        }
        log.info("[{}] fetchAllCountries completed: {} countries with holiday data", getSource(), result.size());
        return result;
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (!isAvailable()) return Optional.empty();
        String iso2 = countryCode.toUpperCase();
        int year = Year.now(clock).getValue();
        try {
            String url = httpClient.buildUrl(normalizedBaseUrl, "/PublicHolidays/" + year + "/" + iso2);
            Optional<String> body = httpClient.getOptional(getSource(), url, timeout);
            if (body.isEmpty()) return Optional.empty();

            List<RawStatistic> stats = parser.parseHolidays(body.get(), iso2, year);
            if (stats.isEmpty()) return Optional.empty();

            return Optional.of(RawCountryData.builder()
                    .isoAlpha2(iso2)
                    .nameEn(iso2)
                    .source(getSource())
                    .statistics(stats)
                    .build());
        } catch (Exception e) {
            log.warn("[{}] Failed to fetch holidays for {}", getSource(), iso2, e);
            return Optional.empty();
        }
    }
}
