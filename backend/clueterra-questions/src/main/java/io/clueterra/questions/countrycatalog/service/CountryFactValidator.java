package io.clueterra.questions.countrycatalog.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.clueterra.questions.countrycatalog.model.CountryAreaFact;
import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import io.clueterra.questions.countrycatalog.model.CountryFactSheet;
import io.clueterra.questions.countrycatalog.model.CountryPeak;
import io.clueterra.questions.countrycatalog.model.CountryPeakFact;
import io.clueterra.questions.countrycatalog.model.CountryPopulationFact;
import io.clueterra.questions.countrycatalog.model.CatalogReference;
import io.clueterra.questions.countrycatalog.model.CatalogStatistic;
import io.clueterra.questions.countrycatalog.model.ValidatedCountry;

@Component
public class CountryFactValidator {

    private static final String POPULATION = "SP.POP.TOTL";
    private static final String AREA = "GEO_AREA";
    private static final String GEONAMES_CAPITAL = "GEO_CAPITAL";
    private static final String CIA_CAPITAL = "CAPITAL_CITY";
    private static final String HIGHEST_POINT = "HIGHEST_POINT";
    private static final String COASTLINE = "COASTLINE";
    private static final String DRIVING_SIDE = "WIKIDATA_DRIVING_SIDE";
    private static final String WORLD_BANK = "WORLD_BANK";
    private static final String CIA_FACTBOOK = "CIA_FACTBOOK";
    private static final String GEONAMES = "GEONAMES";
    private static final String LEFT = "left";
    private static final String ENGLISH = "en";
    private static final Set<String> CAPITAL_EXCLUSIONS = Set.of("IL");
    private static final double POPULATION_TOLERANCE = 0.15;
    private static final double FALLBACK_POPULATION_TOLERANCE = 0.08;
    private static final double AREA_TOLERANCE = 0.1;
    private static final double GEOMETRY_AREA_TOLERANCE = 0.25;
    private static final double ELEVATION_TOLERANCE_M = 15;
    private static final double ELEVATION_TOLERANCE = 0.02;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern ELEVATION = Pattern.compile("(-?\\d[\\d,]*(?:\\.\\d+)?)(?:\\s*m\\b|\\s*$)");

    public ValidatedCountry validate(CountryCatalogEntry entry, List<CatalogStatistic> statistics) {
        CatalogReference reference = entry.reference();
        Optional<CatalogStatistic> worldBank = statistic(statistics, POPULATION, WORLD_BANK);
        Optional<CatalogStatistic> cia = statistic(statistics, POPULATION, CIA_FACTBOOK);
        Optional<CatalogStatistic> geonames = statistic(statistics, POPULATION, GEONAMES);
        Optional<CatalogStatistic> population = Stream.of(worldBank, cia).flatMap(Optional::stream)
                .filter(row -> positive(row) && reference.populations().stream()
                        .anyMatch(value -> agrees(value, row.value(), POPULATION_TOLERANCE)))
                .findFirst()
                .or(() -> worldBank.isEmpty() && cia.isEmpty() ? geonames.filter(row -> positive(row)
                        && reference.populations().stream()
                        .anyMatch(value -> agrees(value, row.value(), FALLBACK_POPULATION_TOLERANCE))) : Optional.empty());

        Optional<CatalogStatistic> area = statistic(statistics, AREA, GEONAMES)
                .filter(row -> positive(row) && (reference.areas().stream().anyMatch(value -> agrees(value, row.value(), AREA_TOLERANCE))
                        || agrees(reference.geometryAreaKm2(), row.value(), GEOMETRY_AREA_TOLERANCE)));

        boolean excluded = CAPITAL_EXCLUSIONS.contains(entry.iso());
        List<String> ourCapitals = Stream.of(statistic(statistics, GEONAMES_CAPITAL, null), statistic(statistics, CIA_CAPITAL, null))
                .flatMap(Optional::stream).map(row -> normalize(row.text())).filter(text -> !text.isEmpty()).toList();
        List<Map<String, String>> capitals = reference.capitals();
        boolean capitalConfirmed = !excluded && capitals.stream().anyMatch(capital -> matches(ourCapitals, capital.get(ENGLISH)));
        Map<String, String> capital = capitalConfirmed && capitals.size() == 1 ? capitals.getFirst() : null;

        Optional<Double> ourElevation = statistic(statistics, HIGHEST_POINT, null).flatMap(row -> elevation(row.text()));
        Optional<CountryPeak> peak = ourElevation.flatMap(elevation -> reference.peaks().stream()
                .filter(candidate -> Math.abs(candidate.elevation() - elevation)
                        <= Math.max(ELEVATION_TOLERANCE_M, Math.abs(elevation) * ELEVATION_TOLERANCE))
                .findFirst());

        boolean landlocked = statistic(statistics, COASTLINE, null).map(CatalogStatistic::value)
                .filter(value -> value == 0).isPresent() && reference.landlocked();
        boolean drivesLeft = statistic(statistics, DRIVING_SIDE, null).map(CatalogStatistic::text)
                .filter(LEFT::equals).isPresent();

        CountryFactSheet facts = new CountryFactSheet(capital,
                population.map(row -> new CountryPopulationFact(Math.round(row.value()), row.year(), row.source())).orElse(null),
                area.map(row -> new CountryAreaFact(row.value())).orElse(null),
                peak.map(found -> new CountryPeakFact(found.name(), Math.round(found.elevation()))).orElse(null),
                landlocked, drivesLeft, null, null);
        double rankArea = area.map(CatalogStatistic::value)
                .or(() -> reference.areas().stream().findFirst())
                .orElse(reference.geometryAreaKm2());
        double rankPopulation = population.map(CatalogStatistic::value)
                .orElseGet(() -> reference.populations().stream().mapToDouble(Double::doubleValue).max().orElse(0));
        return new ValidatedCountry(facts, capitalConfirmed ? capitals : null, rankArea, rankPopulation);
    }

    private Optional<CatalogStatistic> statistic(List<CatalogStatistic> statistics, String indicator, String source) {
        return statistics.stream()
                .filter(row -> indicator.equals(row.indicator()) && (source == null || source.equals(row.source())))
                .findFirst();
    }

    private boolean positive(CatalogStatistic row) {
        return row.value() != null && row.value() > 0;
    }

    private boolean agrees(Double reference, Double value, double tolerance) {
        return reference != null && value != null && reference > 0 && value > 0
                && Math.abs(reference - value) / Math.max(reference, value) <= tolerance;
    }

    private boolean matches(List<String> ourCapitals, String capital) {
        String candidate = normalize(capital);
        return !candidate.isEmpty() && ourCapitals.stream().anyMatch(ours -> ours.contains(candidate) || candidate.contains(ours));
    }

    private Optional<Double> elevation(String text) {
        Matcher matcher = ELEVATION.matcher(Objects.requireNonNullElse(text, ""));
        return matcher.find() ? Optional.of(Double.parseDouble(matcher.group(1).replace(",", ""))) : Optional.empty();
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(Objects.requireNonNullElse(value, ""), Normalizer.Form.NFKD);
        return NON_ALPHANUMERIC.matcher(DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
