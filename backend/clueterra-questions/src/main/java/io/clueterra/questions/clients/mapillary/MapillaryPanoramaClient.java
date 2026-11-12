package io.clueterra.questions.clients.mapillary;

import java.time.Clock;
import io.clueterra.questions.clients.common.AbstractStaticClient;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.mapillary.dto.MapillaryCapitalCoordinate;
import io.clueterra.questions.clients.mapillary.dto.MapillaryStoredImage;
import io.clueterra.questions.clients.mapillary.hintgeneration.MapillaryPanoramaHintSupport;
import io.clueterra.questions.clients.mapillary.repository.MapillaryCapitalCoordinateRepository;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MapillaryPanoramaClient extends AbstractStaticClient {

    private static final BigDecimal CONFIDENCE = BigDecimal.valueOf(0.85);
    private static final BigDecimal WEIGHT = BigDecimal.valueOf(0.90);

    private static final Map<String, String> BASE_INDICATOR_NAMES = Map.of(
            "MAPILLARY_LANDMARK_PANO", "Mapillary landmark panorama",
            "MAPILLARY_CITY_PANO", "Mapillary city panorama",
            "MAPILLARY_RURAL_PANO", "Mapillary rural panorama"
    );

    private final HttpClientHelper httpClient;
    private final SourcesProperties sourcesProperties;
    private final MapillaryPanoramaFetcher fetcher;
    private final MapillaryCityProvider cityProvider;
    private final MapillaryCapitalCoordinateRepository capitalCoordinateRepository;
    private final Clock clock;

    @PostConstruct
    public void init() {
        initBase(httpClient, sourcesProperties);
        if (isDisabled()) {
            log.info("[{}] Client is disabled", getSource());
            return;
        }
        this.apiKey = props.getApiKey();
        log.info("=== Mapillary Panorama Client Initialization ===");
        log.info("[{}] Base URL: {}", getSource(), normalizedBaseUrl);
        log.info("[{}] Rate limit: {} req/sec", getSource(), props.getRateLimitPerSecond());
        log.info("[{}] Timeout: {}s", getSource(), props.getTimeoutSeconds());
        log.info("[{}] API key configured: {}", getSource(), StringUtils.isNotBlank(apiKey));
        log.info("[{}] Enabled: {}", getSource(), props.isEnabled());
        log.info("=================================================");
    }

    @Override
    public DataSource getSource() {
        return DataSource.MAPILLARY;
    }

    @Override
    public boolean isAvailable() {
        if (isDisabled()) {
            return false;
        }
        if (StringUtils.isBlank(apiKey)) {
            return false;
        }

        String healthUrl = UriComponentsBuilder.fromUriString(normalizedBaseUrl + "/images")
                .queryParam("access_token", apiKey)
                .queryParam("fields", "id")
                .queryParam("limit", 1)
                .build()
                .encode()
                .toUriString();

        return httpClient.healthCheck(getSource(), healthUrl, timeout);
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (!isAvailable()) {
            return List.of();
        }

        List<MapillaryCapitalCoordinate> capitals = capitalCoordinateRepository.findAll();

        List<RawCountryData> result = new ArrayList<>();
        int year = Year.now(clock).getValue();
        int total = capitals.size();
        int nextLogThreshold = Math.max(1, total / 5);
        int landmarkCount = 0;
        int cityCount = 0;
        int ruralCount = 0;

        for (int i = 0; i < total; i++) {
            MapillaryCapitalCoordinate capital = capitals.get(i);
            Optional<RawCountryData> data = buildCountryData(capital, year);
            if (data.isPresent()) {
                RawCountryData country = data.get();
                result.add(country);
                for (RawStatistic stat : country.getStatistics()) {
                    String base = MapillaryPanoramaHintSupport.extractBase(stat.indicatorCode());
                    if ("MAPILLARY_LANDMARK_PANO".equals(base)) {
                        landmarkCount++;
                    } else if ("MAPILLARY_CITY_PANO".equals(base)) {
                        cityCount++;
                    } else if ("MAPILLARY_RURAL_PANO".equals(base)) {
                        ruralCount++;
                    }
                }
            }
            if (progressListener != null) {
                progressListener.onProgress(i + 1, total, capital.countryCode());
            }
            if ((i + 1) % nextLogThreshold == 0) {
                log.info("[{}] Progress: {}/{} countries scanned, {} with panoramas",
                        getSource(), i + 1, total, result.size());
            }
        }

        log.info("[{}] Completed: {} countries — {} landmark, {} city, {} rural panoramas",
                getSource(), result.size(), landmarkCount, cityCount, ruralCount);
        return result;
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        String iso2 = countryCode == null ? null : countryCode.trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.isNotBlank(iso2)) {
            return Optional.empty();
        }

        return capitalCoordinateRepository.findByCountryCode(iso2)
                .flatMap(capital -> buildCountryData(capital, Year.now(clock).getValue()));
    }

    private Optional<RawCountryData> buildCountryData(MapillaryCapitalCoordinate capital, int year) {
        List<double[]> cities = cityProvider.getCities(capital.countryCode());
        if (cities.isEmpty()) {
            cities = List.of(new double[]{capital.latitude().doubleValue(), capital.longitude().doubleValue()});
        }
        List<double[]> rural = cityProvider.getRural(capital.countryCode());
        List<double[]> landmarks = cityProvider.getLandmarks(capital.countryCode());

        Map<String, MapillaryStoredImage> panoramas = fetcher.fetchPanoramas(
                capital.countryCode(), landmarks, cities, rural, normalizedBaseUrl, apiKey, timeout);
        if (panoramas.isEmpty()) {
            return Optional.empty();
        }

        List<RawStatistic> stats = panoramas.entrySet().stream()
                .map(entry -> new RawStatistic(
                        entry.getKey(),
                        indicatorName(entry.getKey()),
                        Category.GEOGRAPHY,
                        null,
                        "360° panoramic view",
                        "panorama",
                        year,
                        CONFIDENCE,
                        WEIGHT,
                        entry.getValue().imageUrl(),
                        entry.getValue().originalImageUrl(),
                        entry.getValue().sourcePageUrl(),
                        entry.getValue().authorName(),
                        entry.getValue().attributionText(),
                        entry.getValue().licenseLabel(),
                        entry.getValue().licenseUrl()
                ))
                .toList();

        return Optional.of(RawCountryData.builder()
                .isoAlpha2(capital.countryCode())
                .nameEn(capital.capitalName())
                .source(getSource())
                .statistics(stats)
                .build());
    }

    private static String indicatorName(String code) {
        String base = MapillaryPanoramaHintSupport.extractBase(code);
        String suffix = code.substring(base.length());
        String baseName = BASE_INDICATOR_NAMES.getOrDefault(base, code);
        return suffix.isEmpty() ? baseName : baseName + " " + suffix.substring(1);
    }
}
