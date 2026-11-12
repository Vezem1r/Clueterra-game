package io.clueterra.questions.clients.worldbank;

import io.clueterra.questions.clients.common.dto.RawCountryDataBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import io.clueterra.questions.clients.common.AbstractStaticClient;
import io.clueterra.questions.clients.common.DataSourceException;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.FetchFailure;
import io.clueterra.questions.clients.common.dto.FetchResult;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorldBankClient extends AbstractStaticClient {

    private static final Map<String, IndicatorConfig> INDICATORS = WorldBankIndicators.ALL;

    private final HttpClientHelper httpClient;
    private final SourcesProperties sourcesProperties;
    private final WorldBankIndicatorService worldBankIndicatorService;

    @PostConstruct
    public void init() {
        initBase(httpClient, sourcesProperties);
        if (isDisabled()) {
            log.info("[{}] Client is disabled", getSource());
            return;
        }
        log.info("=== WorldBank Client Initialization ===");
        log.info("[{}] Base URL: {}", getSource(), normalizedBaseUrl);
        log.info("[{}] Rate limit: {} req/sec", getSource(), props.getRateLimitPerSecond());
        log.info("[{}] Timeout: {}s", getSource(), props.getTimeoutSeconds());
        log.info("[{}] Year range: {}", getSource(), props.getAgeRange());
        log.info("[{}] Enabled: {}", getSource(), props.isEnabled());
        log.info("========================================");
    }

    @Override
    public DataSource getSource() {
        return DataSource.WORLD_BANK;
    }

    @Override
    public boolean isAvailable() {
        if (isDisabled()) {
            return false;
        }
        String healthUrl = httpClient.buildUrl(normalizedBaseUrl, "/country?format=json&per_page=1");
        return httpClient.healthCheck(getSource(), healthUrl, timeout);
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (isDisabled()) {
            return List.of();
        }

        log.info("[{}] === Starting fetch all countries with indicators ===", getSource());
        lastFetchResult.set(null);

        try {
            String url = httpClient.buildUrl(normalizedBaseUrl, "/country?format=json&per_page=500");
            String response = httpClient.get(getSource(), url, timeout);

            List<RawCountryData> countries = parseAllCountries(response);

            log.info("[{}] Fetched {} countries, now enriching with indicators", getSource(), countries.size());

            List<RawCountryData> enrichedCountries = enrichCountriesWithIndicators(countries);

            log.info("[{}] === Fetch completed: {} countries enriched ===", getSource(), enrichedCountries.size());
            return enrichedCountries;

        } catch (DataSourceException e) {
            log.error("[{}] === Fetch FAILED ===", getSource(), e);
            throw e;
        }
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (isDisabled()) {
            return Optional.empty();
        }

        String normalizedCode = httpClient.normalizeCountryCode(countryCode);
        log.info("[{}] Fetching country: {}", getSource(), normalizedCode);
        lastFetchResult.set(null);

        String url = httpClient.buildUrl(normalizedBaseUrl, "/country/" + normalizedCode + "?format=json");
        return httpClient.getOptional(getSource(), url, timeout)
                .flatMap(this::parseSingleCountry)
                .map(country -> enrichCountriesWithIndicators(List.of(country)).getFirst());
    }

    private List<RawCountryData> enrichCountriesWithIndicators(List<RawCountryData> countries) {
        long startTime = System.currentTimeMillis();
        log.info("[{}] Starting enrichment of {} countries", getSource(), countries.size());

        List<CompletableFuture<WorldBankCountryIndicators>> futures = countries.stream()
                .map(this::fetchIndicators)
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<FetchFailure> failures = new ArrayList<>();
        List<RawCountryData> enrichedCountries = new ArrayList<>(countries.size());
        for (int i = 0; i < countries.size(); i++) {
            RawCountryData country = countries.get(i);
            WorldBankCountryIndicators indicators = futures.get(i).join();
            if (!indicators.complete()) {
                failures.add(new FetchFailure(country.getIsoAlpha2(), "INDICATORS",
                        "Failed indicators: " + String.join(", ", indicators.failedIndicators())));
            }
            enrichedCountries.add(withStatistics(country, indicators));
        }
        lastFetchResult.set(new FetchResult(enrichedCountries.size(), failures));

        long duration = Math.max(1, System.currentTimeMillis() - startTime);
        log.info("[{}] Completed enrichment of {} countries in {}ms ({} countries/sec), {} incomplete",
                getSource(), enrichedCountries.size(), duration,
                String.format("%.2f", enrichedCountries.size() * 1000.0 / duration), failures.size());
        return enrichedCountries;
    }

    private CompletableFuture<WorldBankCountryIndicators> fetchIndicators(RawCountryData country) {
        return worldBankIndicatorService.fetchIndicatorsForCountryAsync(
                        normalizedBaseUrl, country.getIsoAlpha2(), props.getAgeRange(), timeout, INDICATORS)
                .exceptionally(ex -> {
                    log.warn("[{}] Failed to fetch indicators for {}", getSource(), country.getIsoAlpha2(), ex);
                    return new WorldBankCountryIndicators(List.of(), List.copyOf(INDICATORS.keySet()));
                });
    }

    private RawCountryData withStatistics(RawCountryData country, WorldBankCountryIndicators indicators) {
        return RawCountryData.builder()
                .isoAlpha2(country.getIsoAlpha2())
                .isoAlpha3(country.getIsoAlpha3())
                .nameEn(country.getNameEn())
                .nameNative(country.getNameNative().orElse(null))
                .region(country.getRegion().orElse(null))
                .subregion(country.getSubregion().orElse(null))
                .capital(country.getCapital().orElse(null))
                .source(DataSource.WORLD_BANK)
                .statistics(indicators.statistics())
                .statisticsComplete(indicators.complete())
                .build();
    }

    private List<RawCountryData> parseAllCountries(String responseBody) {
        try {
            List<JsonNode> countries = worldBankIndicatorService.parseWorldBankArray(responseBody);

            return countries.stream()
                    .map(this::parseCountryNode)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

        } catch (Exception e) {
            throw DataSourceException.parseError(getSource(), "/country", e);
        }
    }

    private Optional<RawCountryData> parseSingleCountry(String responseBody) {
        try {
            List<JsonNode> countries = worldBankIndicatorService.parseWorldBankArray(responseBody);

            if (countries.isEmpty()) {
                return Optional.empty();
            }

            return parseCountryNode(countries.getFirst());

        } catch (Exception e) {
            throw DataSourceException.parseError(getSource(), "/country", e);
        }
    }

    private Optional<RawCountryData> parseCountryNode(JsonNode node) {
        if (!isValidCountry(node)) {
            return Optional.empty();
        }

        String iso2 = getText(node, "iso2Code");
        String name = getText(node, "name");

        if (iso2 == null || name == null) {
            return Optional.empty();
        }

        RawCountryDataBuilder builder = RawCountryData.builder()
                .source(DataSource.WORLD_BANK)
                .isoAlpha2(iso2)
                .isoAlpha3(getText(node, "id"))
                .nameEn(name);

        JsonNode regionNode = node.get("region");
        if (regionNode != null) {
            builder.region(getText(regionNode, "value"));
        }

        String capital = getText(node, "capitalCity");
        if (capital != null && !capital.isBlank()) {
            builder.capital(capital);
        }

        String lat = getText(node, "latitude");
        String lon = getText(node, "longitude");
        if (lat != null && lon != null) {
            try {
                builder.coordinates(Double.parseDouble(lat), Double.parseDouble(lon));
            } catch (NumberFormatException ignored) {
                log.warn("[WorldBank] Failed to parse coordinates for country {}: lat='{}', lon='{}'", iso2, lat, lon);
            }
        }

        return Optional.of(builder.build());
    }

    private boolean isValidCountry(JsonNode node) {
        String iso2 = getText(node, "iso2Code");
        if (iso2 == null || iso2.length() != 2) {
            return false;
        }

        JsonNode regionNode = node.get("region");
        if (regionNode != null) {
            String regionValue = getText(regionNode, "value");
            return !"Aggregates".equals(regionValue);
        }

        return true;
    }

    private String getText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.asText();
        return text.isBlank() ? null : text.trim();
    }
}
