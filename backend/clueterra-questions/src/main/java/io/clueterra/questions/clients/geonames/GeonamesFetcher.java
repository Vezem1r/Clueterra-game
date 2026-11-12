package io.clueterra.questions.clients.geonames;

import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeonamesFetcher {

    private final HttpClientHelper httpClient;
    private final GeonamesParser parser;

    private static final String FEATURE_CLASS_CITIES = "P";
    private static final String FEATURE_CLASS_TERRAIN = "T";
    private static final String FEATURE_CLASS_HYDRO = "H";
    private static final String FEATURE_CLASS_SPOT = "S";
    private static final String FEATURE_CLASS_ADMIN = "A";

    private static final String FEATURE_CODE_MOUNTAIN = "MT";
    private static final String FEATURE_CODE_AIRPORT = "AIRP";
    private static final String FEATURE_CODE_LAKE = "LK";
    private static final String FEATURE_CODE_RIVER = "STM";
    private static final String FEATURE_CODE_ISLAND = "ISL";
    private static final String FEATURE_CODE_ADM1 = "ADM1";

    private static final BigDecimal WEIGHT_MEDIUM = BigDecimal.valueOf(0.60);

    public boolean healthCheck(String baseUrl, String username, Duration timeout) {
        String healthUrl = buildCountryInfoEndpoint(baseUrl, "US", username);
        return httpClient.healthCheck(getSource(), healthUrl, timeout);
    }

    public List<RawCountryData> fetchAllBasicCountries(String baseUrl, String username, Duration timeout) {
        String url = buildCountryInfoEndpoint(baseUrl, null, username);
        String response = httpClient.get(getSource(), url, timeout);
        return parser.parseAllCountries(response);
    }

    public Optional<RawCountryData> fetchBasicCountryByCode(String countryCode, String baseUrl, String username, Duration timeout) {
        String url = buildCountryInfoEndpoint(baseUrl, countryCode, username);
        return httpClient.getOptional(getSource(), url, timeout).flatMap(parser::parseSingleCountry);
    }

    public List<RawStatistic> fetchAllForCountry(String countryCode, String baseUrl, String username, Duration timeout) {
        return Stream.of(
                        safeFetch(() -> fetchCitiesData(countryCode, baseUrl, username, timeout), "cities", countryCode),
                        safeFetch(() -> fetchMountainsData(countryCode, baseUrl, username, timeout), "mountains", countryCode),
                        safeFetch(() -> fetchAdminDivisionsData(countryCode, baseUrl, username, timeout), "admin divisions", countryCode),
                        safeFetch(() -> fetchNeighboursData(countryCode, baseUrl, username, timeout), "neighbours", countryCode),
                        safeFetch(() -> fetchPostalCodesData(countryCode, baseUrl, username, timeout), "postal codes", countryCode),
                        safeFetch(() -> fetchFeatureCounts(countryCode, baseUrl, username, timeout), "feature counts", countryCode)
                )
                .flatMap(List::stream)
                .toList();
    }

    private List<RawStatistic> safeFetch(Supplier<List<RawStatistic>> fetcher,
                                         String type, String countryCode) {
        try {
            return fetcher.get();
        } catch (Exception e) {
            log.warn("[{}] Could not fetch {} for {}", getSource(), type, countryCode, e);
            return List.of();
        }
    }

    private List<RawStatistic> fetchCitiesData(String countryCode, String baseUrl, String username, Duration timeout) {
        String url = buildSearchEndpoint(baseUrl, countryCode, FEATURE_CLASS_CITIES, null, "population", 50, username);
        String response = httpClient.get(getSource(), url, timeout);
        return parser.parseCities(response, countryCode);
    }

    private List<RawStatistic> fetchMountainsData(String countryCode, String baseUrl, String username, Duration timeout) {
        String url = buildSearchEndpoint(baseUrl, countryCode, FEATURE_CLASS_TERRAIN, FEATURE_CODE_MOUNTAIN, "elevation", 20, username);
        String response = httpClient.get(getSource(), url, timeout);
        return new ArrayList<>(parser.parseMountains(response, countryCode));
    }

    private List<RawStatistic> fetchAdminDivisionsData(String countryCode, String baseUrl, String username, Duration timeout) {
        String url = buildSearchEndpoint(baseUrl, countryCode, FEATURE_CLASS_ADMIN, FEATURE_CODE_ADM1, null, 100, username);
        String response = httpClient.get(getSource(), url, timeout);
        return parser.parseAdminDivisions(response, countryCode);
    }

    private List<RawStatistic> fetchNeighboursData(String countryCode, String baseUrl, String username, Duration timeout) {
        String url = String.format("%s/neighboursJSON?country=%s&username=%s", baseUrl, countryCode, username);
        String response = httpClient.get(getSource(), url, timeout);
        return parser.parseNeighbours(response, countryCode);
    }

    private List<RawStatistic> fetchPostalCodesData(String countryCode, String baseUrl, String username, Duration timeout) {
        String url = String.format("%s/postalCodeSearchJSON?country=%s&maxRows=1&username=%s", baseUrl, countryCode, username);
        String response = httpClient.get(getSource(), url, timeout);
        return parser.parsePostalCodes(response, countryCode);
    }

    private List<RawStatistic> fetchFeatureCounts(String countryCode, String baseUrl, String username, Duration timeout) {
        List<RawStatistic> stats = new ArrayList<>();

        fetchFeatureCount(countryCode, FEATURE_CLASS_SPOT, FEATURE_CODE_AIRPORT, "GEO_AIRPORT_COUNT", "Number of Airports", "airports", Category.INFRASTRUCTURE, baseUrl, username, timeout)
                .ifPresent(stats::add);

        fetchFeatureCount(countryCode, FEATURE_CLASS_HYDRO, FEATURE_CODE_LAKE, "GEO_LAKE_COUNT", "Number of Lakes", "lakes", Category.GEOGRAPHY, baseUrl, username, timeout)
                .ifPresent(stats::add);

        fetchFeatureCount(countryCode, FEATURE_CLASS_HYDRO, FEATURE_CODE_RIVER, "GEO_RIVER_COUNT", "Number of Named Rivers", "rivers", Category.GEOGRAPHY, baseUrl, username, timeout)
                .ifPresent(stats::add);

        fetchFeatureCount(countryCode, FEATURE_CLASS_TERRAIN, FEATURE_CODE_ISLAND, "GEO_ISLAND_COUNT", "Number of Islands", "islands", Category.GEOGRAPHY, baseUrl, username, timeout)
                .ifPresent(stats::add);

        return stats;
    }

    private Optional<RawStatistic> fetchFeatureCount(String countryCode, String featureClass, String featureCode,
                                                     String indicatorCode, String indicatorName, String unit,
                                                     Category category,
                                                     String baseUrl, String username, Duration timeout) {
        String url = buildSearchEndpoint(baseUrl, countryCode, featureClass, featureCode, null, 1, username);
        return httpClient.getOptional(getSource(), url, timeout).flatMap(response -> parser.parseFeatureCount(response, countryCode, indicatorCode, indicatorName, unit, category, WEIGHT_MEDIUM));
    }

    private String buildSearchEndpoint(String baseUrl, String countryCode, String featureClass, String featureCode, String orderBy, int maxRows, String username) {
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("/searchJSON?country=").append(countryCode);
        url.append("&username=").append(username);
        if (featureClass != null) url.append("&featureClass=").append(featureClass);
        if (featureCode != null) url.append("&featureCode=").append(featureCode);
        if (orderBy != null) url.append("&orderby=").append(orderBy);
        url.append("&maxRows=").append(maxRows);
        return url.toString();
    }

    private DataSource getSource() {
        return DataSource.GEONAMES;
    }

    private String buildCountryInfoEndpoint(String baseUrl, String countryCode, String username) {
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("/countryInfoJSON");
        url.append("?username=").append(username);
        if (countryCode != null) {
            url.append("&country=").append(countryCode);
        }
        return url.toString();
    }
}
