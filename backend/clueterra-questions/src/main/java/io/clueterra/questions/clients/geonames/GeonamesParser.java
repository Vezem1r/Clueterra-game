package io.clueterra.questions.clients.geonames;

import java.time.Clock;

import io.clueterra.questions.clients.common.dto.RawCountryDataBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.DataSourceException;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeonamesParser {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    private static final BigDecimal WEIGHT_UNIQUE = BigDecimal.valueOf(0.95);
    private static final BigDecimal WEIGHT_HIGH = BigDecimal.valueOf(0.8);
    private static final BigDecimal WEIGHT_MEDIUM = BigDecimal.valueOf(0.55);
    private static final BigDecimal WEIGHT_LOW = BigDecimal.valueOf(0.40);

    private static final BigDecimal CONFIDENCE_HIGH = BigDecimal.valueOf(0.95);
    private static final BigDecimal CONFIDENCE_MEDIUM = BigDecimal.valueOf(0.85);

    private static final String ROOT_GEONAMES = "geonames";
    private static final String LANGUAGES = "languages";
    private static final String PEOPLE = "people";

    private static final String CITY_INDICATOR = "GEO_CITY_";
    private static final String RESULTS = "totalResultsCount";
    private static final String PEOPLE_AMOUNT = "%,d people";

    public List<RawCountryData> parseAllCountries(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode geonames = root.path(ROOT_GEONAMES);

            if (!geonames.isArray()) {
                log.warn("[{}}] No 'geonames' array in response", getSource());
                return List.of();
            }

            List<RawCountryData> result = new ArrayList<>();
            for (JsonNode country : geonames) {
                try {
                    RawCountryData data = parseCountryInfoNode(country);
                    if (data != null && data.isValid()) {
                        result.add(data);
                    }
                } catch (Exception e) {
                    log.warn("[{}] Failed to parse country node", getSource(), e);
                }
            }

            return result;

        } catch (Exception e) {
            throw DataSourceException.parseError(DataSource.GEONAMES, "/countryInfoJSON", e);
        }
    }

    public Optional<RawCountryData> parseSingleCountry(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode geonames = root.path(ROOT_GEONAMES);

            if (geonames.isArray() && !geonames.isEmpty()) {
                RawCountryData data = parseCountryInfoNode(geonames.get(0));
                return data != null && data.isValid() ? Optional.of(data) : Optional.empty();
            }

            return Optional.empty();

        } catch (Exception e) {
            throw DataSourceException.parseError(DataSource.GEONAMES, "/countryInfoJSON (single)", e);
        }
    }

    private RawCountryData parseCountryInfoNode(JsonNode node) {
        String countryCode = getText(node, "countryCode");
        if (countryCode == null) {
            return null;
        }

        RawCountryDataBuilder builder = RawCountryData.builder()
                .source(DataSource.GEONAMES)
                .isoAlpha2(countryCode)
                .isoAlpha3(getText(node, "isoAlpha3"))
                .nameEn(getText(node, "countryName"))
                .capital(getText(node, "capital"));

        String continent = getText(node, "continent");
        if (continent != null) {
            builder.continents(List.of(getContinentName(continent)));
        }

        String languages = getText(node, LANGUAGES);
        if (languages != null) {
            builder.languages(Arrays.asList(languages.split(",")));
        }

        String currencyCode = getText(node, "currencyCode");
        if (currencyCode != null) {
            builder.currencies(List.of(currencyCode));
        }

        List<RawStatistic> stats = new ArrayList<>();
        addCountryInfoStatistics(stats, node);
        builder.statistics(stats);

        return builder.build();
    }

    private void addCountryInfoStatistics(List<RawStatistic> stats, JsonNode node) {
        BigDecimal population = getDecimal(node, "population");
        if (population != null && population.compareTo(BigDecimal.ZERO) > 0) {
            stats.add(new RawStatistic(
                    "SP.POP.TOTL", "Total Population", Category.DEMOGRAPHICS,
                    population, null, PEOPLE, currentYear(),
                    CONFIDENCE_HIGH, WEIGHT_UNIQUE));
        }

        BigDecimal area = getDecimal(node, "areaInSqKm");
        if (area != null && area.compareTo(BigDecimal.ZERO) > 0) {
            stats.add(new RawStatistic(
                    "GEO_AREA", "Land Area", Category.GEOGRAPHY,
                    area, null, "km²", currentYear(),
                    CONFIDENCE_HIGH, WEIGHT_UNIQUE));

            if (population != null && population.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal density = population.divide(area, 2, RoundingMode.HALF_UP);
                stats.add(new RawStatistic(
                        "EN.POP.DNST", "Population Density", Category.DEMOGRAPHICS,
                        density, null, "people/km²", currentYear(),
                        CONFIDENCE_HIGH, WEIGHT_HIGH));
            }
        }

        Double north = getDouble(node, "north");
        Double south = getDouble(node, "south");
        Double east = getDouble(node, "east");
        Double west = getDouble(node, "west");

        if (north != null && south != null && east != null && west != null) {
            double latSpan = north - south;
            double lonSpan = east - west;
            if (lonSpan < 0) lonSpan += 360;

            double nsDistanceKm = latSpan * 111;
            double avgLat = (north + south) / 2;
            double ewDistanceKm = lonSpan * 111 * Math.cos(Math.toRadians(avgLat));

            stats.add(new RawStatistic(
                    "GEO_NS_DISTANCE", "North-South Distance", Category.GEOGRAPHY,
                    BigDecimal.valueOf(nsDistanceKm).setScale(0, RoundingMode.HALF_UP),
                    String.format(Locale.US, "~%,.0f km", nsDistanceKm), "km", currentYear(),
                    CONFIDENCE_MEDIUM, WEIGHT_LOW));

            stats.add(new RawStatistic(
                    "GEO_EW_DISTANCE", "East-West Distance", Category.GEOGRAPHY,
                    BigDecimal.valueOf(ewDistanceKm).setScale(0, RoundingMode.HALF_UP),
                    String.format(Locale.US, "~%,.0f km", ewDistanceKm), "km", currentYear(),
                    CONFIDENCE_MEDIUM, WEIGHT_LOW));
        }

        String capital = getText(node, "capital");
        if (capital != null && !capital.isBlank()) {
            stats.add(new RawStatistic(
                    "GEO_CAPITAL", "Capital City", Category.CULTURE,
                    null, capital, null, currentYear(),
                    CONFIDENCE_HIGH, WEIGHT_UNIQUE));
        }

        String continent = getText(node, "continent");
        if (continent != null) {
            String continentName = getContinentName(continent);
            stats.add(new RawStatistic(
                    "GEO_CONTINENT", "Continent", Category.GEOGRAPHY,
                    null, continentName, null, currentYear(),
                    CONFIDENCE_HIGH, WEIGHT_MEDIUM));
        }

        String languages = getText(node, LANGUAGES);
        if (languages != null) {
            String[] langArray = languages.split(",");
            stats.add(new RawStatistic(
                    "GEO_LANGUAGE_COUNT", "Number of Official Languages", Category.CULTURE,
                    BigDecimal.valueOf(langArray.length), null, LANGUAGES, currentYear(),
                    CONFIDENCE_MEDIUM, WEIGHT_MEDIUM));

            if (langArray.length > 0) {
                stats.add(new RawStatistic(
                        "GEO_PRIMARY_LANGUAGE", "Primary Language Code", Category.CULTURE,
                        null, langArray[0].trim(), null, currentYear(),
                        CONFIDENCE_MEDIUM, WEIGHT_UNIQUE));
            }
        }

        String currencyCode = getText(node, "currencyCode");
        if (currencyCode != null) {
            stats.add(new RawStatistic(
                    "GEO_CURRENCY_CODE", "Currency Code", Category.ECONOMY,
                    null, currencyCode, null, currentYear(),
                    CONFIDENCE_HIGH, WEIGHT_UNIQUE));
        }

        String phone = getText(node, "phone");
        if (phone != null && !phone.isBlank()) {
            try {
                String cleanPhone = phone.replaceAll("\\D", "");
                if (!cleanPhone.isEmpty()) {
                    stats.add(new RawStatistic(
                            "GEO_PHONE_PREFIX", "Phone Country Code", Category.INFRASTRUCTURE,
                            new BigDecimal(cleanPhone), "+" + cleanPhone, null, currentYear(),
                            CONFIDENCE_HIGH, WEIGHT_HIGH));
                }
            } catch (NumberFormatException e) {
                log.debug("Could not parse phone prefix: {}", phone);
            }
        }

        String tld = getText(node, "tld");
        if (tld != null && !tld.isBlank()) {
            stats.add(new RawStatistic(
                    "GEO_TLD", "Top-Level Domain", Category.INFRASTRUCTURE,
                    null, tld.startsWith(".") ? tld : "." + tld, null, currentYear(),
                    CONFIDENCE_HIGH, WEIGHT_HIGH));
        }

    }

    public List<RawStatistic> parseCities(String responseBody, String countryCode) {
        List<RawStatistic> stats = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode geonames = root.path(ROOT_GEONAMES);

            if (!geonames.isArray() || geonames.isEmpty()) {
                return stats;
            }

            int rank = 0;
            Long largestCityPop = null;
            String largestCityName;
            long top3Pop = 0;
            long top5Pop = 0;
            long top10Pop = 0;

            for (JsonNode city : geonames) {
                rank++;
                String name = getText(city, "name");
                String toponymName = getText(city, "toponymName");
                Long population = getLong(city);
                String adminName = getText(city, "adminName1");

                if (name == null || population == null || population <= 0) {
                    continue;
                }

                // Track cumulative populations
                if (rank <= 3) top3Pop += population;
                if (rank <= 5) top5Pop += population;
                if (rank <= 10) top10Pop += population;

                String desc = adminName != null
                        ? String.format(Locale.US, "%s, %s (pop. %,d)", name, adminName, population)
                        : String.format(Locale.US, "%s (pop. %,d)", name, population);

                BigDecimal cityWeight;

                if (rank <= 3) {
                    cityWeight = WEIGHT_HIGH;
                } else if (rank <= 10) {
                    cityWeight = WEIGHT_MEDIUM;
                } else {
                    cityWeight = WEIGHT_LOW;
                }

                stats.add(new RawStatistic(
                        CITY_INDICATOR + rank, "City #" + rank + " by population", Category.GEOGRAPHY,
                        BigDecimal.valueOf(population), desc, PEOPLE, currentYear(),
                        CONFIDENCE_HIGH, cityWeight));

                if (rank == 1) {
                    largestCityPop = population;
                    largestCityName = toponymName != null ? toponymName : name;

                    stats.add(new RawStatistic(
                            "GEO_LARGEST_CITY", "Largest City", Category.GEOGRAPHY,
                            null, largestCityName, null, currentYear(),
                            CONFIDENCE_HIGH, WEIGHT_HIGH));

                    stats.add(new RawStatistic(
                            "GEO_LARGEST_CITY_POP", "Largest City Population", Category.DEMOGRAPHICS,
                            BigDecimal.valueOf(population), null, PEOPLE, currentYear(),
                            CONFIDENCE_HIGH, WEIGHT_HIGH));
                }

                if (rank == 2 && largestCityPop != null) {
                    double primacyRatio = (double) largestCityPop / population;
                    stats.add(new RawStatistic(
                            "GEO_CITY_PRIMACY", "Urban Primacy Ratio", Category.DEMOGRAPHICS,
                            BigDecimal.valueOf(primacyRatio).setScale(2, RoundingMode.HALF_UP),
                            String.format("%.2f:1 (largest to 2nd largest)", primacyRatio),
                            "ratio", currentYear(),
                            CONFIDENCE_HIGH, WEIGHT_LOW));
                }

            }

            int totalResults = root.path(RESULTS).asInt(0);
            if (totalResults > 0) {
                stats.add(new RawStatistic(
                        "GEO_CITY_COUNT", "Number of Cities/Towns", Category.GEOGRAPHY,
                        BigDecimal.valueOf(totalResults), null, "places", currentYear(),
                        CONFIDENCE_MEDIUM, WEIGHT_LOW));
            }

            if (top3Pop > 0) {
                stats.add(new RawStatistic(
                        "GEO_TOP3_CITIES_POP", "Top 3 Cities Combined Population", Category.DEMOGRAPHICS,
                        BigDecimal.valueOf(top3Pop), String.format(Locale.US, PEOPLE_AMOUNT, top3Pop),
                        PEOPLE, currentYear(),
                        CONFIDENCE_HIGH, WEIGHT_LOW));
            }

            if (top5Pop > 0) {
                stats.add(new RawStatistic(
                        "GEO_TOP5_CITIES_POP", "Top 5 Cities Combined Population", Category.DEMOGRAPHICS,
                        BigDecimal.valueOf(top5Pop), String.format(Locale.US, PEOPLE_AMOUNT, top5Pop),
                        PEOPLE, currentYear(),
                        CONFIDENCE_HIGH, WEIGHT_LOW));
            }

            if (top10Pop > 0) {
                stats.add(new RawStatistic(
                        "GEO_TOP10_CITIES_POP", "Top 10 Cities Combined Population", Category.DEMOGRAPHICS,
                        BigDecimal.valueOf(top10Pop), String.format(Locale.US, PEOPLE_AMOUNT, top10Pop),
                        PEOPLE, currentYear(),
                        CONFIDENCE_HIGH, WEIGHT_LOW));
            }

        } catch (Exception e) {
            log.warn("[{}] Failed to parse cities for {}", getSource(), countryCode, e);
        }

        return stats;
    }

    public List<RawStatistic> parseMountains(String responseBody, String countryCode) {
        List<RawStatistic> stats = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode geonames = root.path(ROOT_GEONAMES);

            if (!geonames.isArray() || geonames.isEmpty()) {
                return stats;
            }

            int rank = 0;
            Integer highestElevation = null;
            String highestName = null;
            int mountainCount = 0;

            for (JsonNode mountain : geonames) {
                rank++;
                String name = getText(mountain, "name");
                Integer elevation = getInt(mountain);

                if (name == null) continue;
                mountainCount++;

                String desc = elevation != null
                        ? String.format("%s (%,d m)", name, elevation)
                        : name;

                BigDecimal mountWeight = rank <= 3 ? WEIGHT_HIGH : WEIGHT_MEDIUM;

                stats.add(new RawStatistic(
                        "GEO_MOUNTAIN_" + rank, "Mountain #" + rank, Category.GEOGRAPHY,
                        elevation != null ? BigDecimal.valueOf(elevation) : null,
                        desc, "m", currentYear(),
                        CONFIDENCE_HIGH, mountWeight));

                if (rank == 1) {
                    highestElevation = elevation;
                    highestName = name;
                }
            }

            if (highestName != null) {
                stats.add(new RawStatistic(
                        "GEO_HIGHEST_MOUNTAIN", "Highest Mountain", Category.GEOGRAPHY,
                        null, highestName, null, currentYear(),
                        CONFIDENCE_HIGH, WEIGHT_HIGH));

                if (highestElevation != null) {
                    stats.add(new RawStatistic(
                            "GEO_HIGHEST_ELEVATION", "Highest Point Elevation", Category.GEOGRAPHY,
                            BigDecimal.valueOf(highestElevation),
                            String.format("%,d meters", highestElevation),
                            "m", currentYear(),
                            CONFIDENCE_HIGH, WEIGHT_HIGH));
                }
            }

            int totalResults = root.path(RESULTS).asInt(mountainCount);
            if (totalResults > 0) {
                stats.add(new RawStatistic(
                        "GEO_MOUNTAIN_COUNT", "Number of Named Mountains", Category.GEOGRAPHY,
                        BigDecimal.valueOf(totalResults), null, "mountains", currentYear(),
                        CONFIDENCE_MEDIUM, WEIGHT_LOW));
            }

        } catch (Exception e) {
            log.warn("[{}] Failed to parse mountains for {}", getSource(), countryCode, e);
        }

        return stats;
    }

    public List<RawStatistic> parseAdminDivisions(String responseBody, String countryCode) {
        List<RawStatistic> stats = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode geonames = root.path(ROOT_GEONAMES);

            if (!geonames.isArray()) {
                return stats;
            }

            int count = geonames.size();
            if (count > 0) {
                stats.add(new RawStatistic(
                        "GEO_ADMIN_DIVISIONS", "Number of Administrative Divisions", Category.GEOGRAPHY,
                        BigDecimal.valueOf(count), null, "regions", currentYear(),
                        CONFIDENCE_HIGH, WEIGHT_LOW));

                List<String> divisionNames = new ArrayList<>();
                for (int i = 0; i < Math.min(10, count); i++) {
                    String name = getText(geonames.get(i), "name");
                    Long pop = getLong(geonames.get(i));
                    if (name != null) {
                        if (pop != null && pop > 0) {
                            divisionNames.add(String.format(Locale.US, "%s (%,d)", name, pop));
                        } else {
                            divisionNames.add(name);
                        }
                    }
                }

                if (!divisionNames.isEmpty()) {
                    stats.add(new RawStatistic(
                            "GEO_MAJOR_DIVISIONS", "Major Administrative Divisions", Category.GEOGRAPHY,
                            null, String.join(", ", divisionNames), null, currentYear(),
                            CONFIDENCE_HIGH, WEIGHT_MEDIUM));
                }
            }

        } catch (Exception e) {
            log.warn("[{}] Failed to parse admin divisions for {}", getSource(), countryCode, e);
        }

        return stats;
    }

    public List<RawStatistic> parseNeighbours(String responseBody, String countryCode) {
        List<RawStatistic> stats = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode geonames = root.path(ROOT_GEONAMES);

            if (!geonames.isArray()) {
                return stats;
            }

            int count = geonames.size();

            stats.add(new RawStatistic(
                    "GEO_NEIGHBOUR_COUNT", "Number of Neighboring Countries", Category.GEOGRAPHY,
                    BigDecimal.valueOf(count), null, "countries", currentYear(),
                    CONFIDENCE_HIGH, WEIGHT_HIGH));

            if (count > 0) {
                List<String> neighbourNames = new ArrayList<>();
                for (JsonNode neighbour : geonames) {
                    String name = getText(neighbour, "countryName");
                    if (name != null) {
                        neighbourNames.add(name);
                    }
                }

                if (!neighbourNames.isEmpty()) {
                    stats.add(new RawStatistic(
                            "GEO_NEIGHBOURS", "Neighboring Countries", Category.GEOGRAPHY,
                            null, String.join(", ", neighbourNames), null, currentYear(),
                            CONFIDENCE_HIGH, WEIGHT_HIGH));
                }
            } else {
                stats.add(new RawStatistic(
                        "GEO_ISLAND_NATION", "Island Nation", Category.GEOGRAPHY,
                        BigDecimal.ONE, "No land borders", "boolean", currentYear(),
                        CONFIDENCE_HIGH, WEIGHT_HIGH));
            }

        } catch (Exception e) {
            log.warn("[{}] Failed to parse neighbours for {}", getSource(), countryCode, e);
        }

        return stats;
    }

    public List<RawStatistic> parsePostalCodes(String responseBody, String countryCode) {
        List<RawStatistic> stats = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int totalCount = root.path(RESULTS).asInt(0);

            if (totalCount > 0) {
                stats.add(new RawStatistic(
                        "GEO_POSTAL_CODE_COUNT", "Number of Postal Codes", Category.INFRASTRUCTURE,
                        BigDecimal.valueOf(totalCount), null, "codes", currentYear(),
                        CONFIDENCE_MEDIUM, WEIGHT_LOW));
            }

        } catch (Exception e) {
            log.warn("[{}] Failed to parse postal codes for {}", getSource(), countryCode, e);
        }

        return stats;
    }

    public Optional<RawStatistic> parseFeatureCount(
            String responseBody,
            String countryCode,
            String indicatorCode,
            String indicatorName,
            String unit,
            Category category,
            BigDecimal weight) {

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int totalCount = root.path(RESULTS).asInt(0);

            if (totalCount > 0) {
                return Optional.of(new RawStatistic(
                        indicatorCode, indicatorName, category,
                        BigDecimal.valueOf(totalCount), null, unit, currentYear(),
                        CONFIDENCE_MEDIUM, weight));
            }

        } catch (Exception e) {
            log.warn("[{}] Failed to parse feature count for {}", getSource(), countryCode, e);
        }

        return Optional.empty();
    }

    private String getText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer getInt(JsonNode node) {
        JsonNode value = node.path("elevation");
        if (value.isMissingNode() || value.isNull()) return null;
        return value.isNumber() ? value.asInt() : null;
    }

    private Long getLong(JsonNode node) {
        JsonNode value = node.path("population");
        if (value.isMissingNode() || value.isNull()) return null;
        return value.isNumber() ? value.asLong() : null;
    }

    private Double getDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        return value.isNumber() ? value.asDouble() : null;
    }

    private BigDecimal getDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (value.isNumber()) {
            return value.decimalValue();
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getContinentName(String code) {
        return switch (code.toUpperCase()) {
            case "AF" -> "Africa";
            case "AN" -> "Antarctica";
            case "AS" -> "Asia";
            case "EU" -> "Europe";
            case "NA" -> "North America";
            case "OC" -> "Oceania";
            case "SA" -> "South America";
            default -> code;
        };
    }

    private DataSource getSource() {
        return DataSource.GEONAMES;
    }

    private int currentYear() {
        return Year.now(clock).getValue();
    }
}
