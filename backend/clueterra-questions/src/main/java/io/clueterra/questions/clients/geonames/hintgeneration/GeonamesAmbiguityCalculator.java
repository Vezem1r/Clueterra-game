package io.clueterra.questions.clients.geonames.hintgeneration;

import io.clueterra.questions.entity.CountryStatistic;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class GeonamesAmbiguityCalculator {

    public int computeLanguage(String language, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        if (language == null) return 1;
        String baseCode = extractBaseCode(language);
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream().anyMatch(s ->
                        "GEO_PRIMARY_LANGUAGE".equals(s.getIndicatorCode())
                                && s.getValueText() != null
                                && baseCode.equals(extractBaseCode(s.getValueText()))))
                .count();
    }

    public int computeLanguageCount(int count, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream().anyMatch(s ->
                        "GEO_LANGUAGE_COUNT".equals(s.getIndicatorCode())
                                && s.getValue() != null
                                && inSameBand(count, s.getValue().intValue())))
                .count();
    }

    public int computeSoloNeighbour(Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream().anyMatch(s ->
                        "GEO_NEIGHBOURS".equals(s.getIndicatorCode())
                                && s.getValueText() != null
                                && countDelimitedItems(s.getValueText()) == 1))
                .count();
    }

    public int computeTwoNeighbours(Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream().anyMatch(s ->
                        "GEO_NEIGHBOURS".equals(s.getIndicatorCode())
                                && s.getValueText() != null
                                && countDelimitedItems(s.getValueText()) == 2))
                .count();
    }

    public int computeNeighbourCountHigh(int count, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream().anyMatch(s ->
                        "GEO_NEIGHBOUR_COUNT".equals(s.getIndicatorCode())
                                && s.getValue() != null
                                && inSameNeighbourBand(count, s.getValue().intValue())))
                .count();
    }

    public int computeIslandNation(Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream().anyMatch(s ->
                        "GEO_ISLAND_NATION".equals(s.getIndicatorCode())))
                .count();
    }

    public int computeLargestCity() {
        return 1;
    }

    public int computeAreaScale(double areaKm2, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        int band = areaBandIndex(areaKm2);
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> findNumericValue(e.getValue(), "GEO_AREA")
                        .map(v -> areaBandIndex(v) == band)
                        .orElse(false))
                .count();
    }

    public int computeElevationBand(int elevation, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        int band = elevationBandIndex(elevation);
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> findNumericValue(e.getValue(), "GEO_HIGHEST_ELEVATION")
                        .map(v -> elevationBandIndex(v.intValue()) == band)
                        .orElse(false))
                .count();
    }

    public int computeCountBand(String indicatorCode, int count, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        int band = featureCountBandIndex(count);
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> findNumericValue(e.getValue(), indicatorCode)
                        .map(v -> featureCountBandIndex(v.intValue()) == band)
                        .orElse(false))
                .count();
    }

    public int computePrimacy(double ratio, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        int band = primacyBandIndex(ratio);
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> findNumericValue(e.getValue(), "GEO_CITY_PRIMACY")
                        .map(v -> primacyBandIndex(v) == band)
                        .orElse(false))
                .count();
    }

    public int computePopulationBand(long population, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        int band = populationBandIndex(population);
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> findNumericValue(e.getValue(), "GEO_LARGEST_CITY_POP")
                        .map(v -> populationBandIndex(v.longValue()) == band)
                        .orElse(false))
                .count();
    }

    public int computeDensityBand(double density, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        int band = densityBandIndex(density);
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> findNumericValue(e.getValue(), "EN.POP.DNST")
                        .map(v -> densityBandIndex(v) == band)
                        .orElse(false))
                .count();
    }

    public int computeCombo(int... ambiguities) {
        return Arrays.stream(ambiguities).min().orElse(1);
    }

    private Optional<Double> findNumericValue(List<CountryStatistic> stats, String indicatorCode) {
        return stats.stream()
                .filter(s -> indicatorCode.equals(s.getIndicatorCode()) && s.getValue() != null)
                .findFirst()
                .map(s -> s.getValue().doubleValue());
    }

    private int areaBandIndex(double areaKm2) {
        if (areaKm2 < 1_000) return 0;
        if (areaKm2 < 10_000) return 1;
        if (areaKm2 < 100_000) return 2;
        if (areaKm2 < 500_000) return 3;
        if (areaKm2 < 1_000_000) return 4;
        if (areaKm2 < 3_000_000) return 5;
        return 6;
    }

    private int elevationBandIndex(int meters) {
        if (meters < 500) return 0;
        if (meters < 2000) return 1;
        if (meters < 4000) return 2;
        if (meters < 6000) return 3;
        return 4;
    }

    private int featureCountBandIndex(int count) {
        if (count < 10) return 0;
        if (count < 50) return 1;
        if (count < 200) return 2;
        if (count < 500) return 3;
        if (count < 1000) return 4;
        return 5;
    }

    private int primacyBandIndex(double ratio) {
        if (ratio < 2) return 0;
        if (ratio < 5) return 1;
        if (ratio < 10) return 2;
        return 3;
    }

    private int populationBandIndex(long pop) {
        if (pop < 100_000) return 0;
        if (pop < 500_000) return 1;
        if (pop < 1_000_000) return 2;
        if (pop < 5_000_000) return 3;
        if (pop < 10_000_000) return 4;
        return 5;
    }

    private int densityBandIndex(double density) {
        if (density < 25) return 0;
        if (density < 100) return 1;
        if (density < 300) return 2;
        if (density < 1000) return 3;
        return 4;
    }

    private boolean inSameBand(int a, int b) {
        return bandIndex(a) == bandIndex(b);
    }

    private int bandIndex(int count) {
        if (count >= 8) return 2;
        if (count >= 6) return 1;
        return 0;
    }

    private boolean inSameNeighbourBand(int a, int b) {
        return neighbourBandIndex(a) == neighbourBandIndex(b);
    }

    private int neighbourBandIndex(int count) {
        if (count >= 11) return 2;
        if (count >= 9) return 1;
        return 0;
    }

    private String extractBaseCode(String langCode) {
        return langCode.replace('_', '-').split("-", 2)[0].toLowerCase(Locale.ENGLISH);
    }

    private int countDelimitedItems(String value) {
        return (int) Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .count();
    }
}
