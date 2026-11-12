package io.clueterra.questions.quizgame.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.clueterra.questions.quizgame.model.QuizCountryProfile;
import io.clueterra.questions.quizgame.model.QuizMetric;
import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import io.clueterra.questions.countrycatalog.model.CountryCurrency;
import io.clueterra.questions.countrycatalog.model.CountryFactSheet;
import io.clueterra.questions.countrycatalog.model.CatalogStatistic;
import io.clueterra.questions.countrycatalog.model.ValidatedCountry;

@Component
public class QuizFactChecker {

    public static final String LEFT = "LEFT";
    public static final String RIGHT = "RIGHT";

    private static final String DRIVING_SIDE = "WIKIDATA_DRIVING_SIDE";
    private static final String CURRENCY_CODE = "GEO_CURRENCY_CODE";
    private static final String CURRENCY_TEXT = "CURRENCY";
    private static final String COASTLINE = "COASTLINE";
    private static final String LIFE_EXPECTANCY_OWID = "OWID_LIFE_EXPECTANCY";
    private static final String LIFE_EXPECTANCY = "SP.DYN.LE00.IN";
    private static final String GDP_PER_CAPITA = "NY.GDP.PCAP.CD";
    private static final String TOURISTS = "OWID_TOURIST_ARRIVALS";
    private static final String FOREST = "FOREST_LAND_PCT";
    private static final String MEDIAN_AGE = "MEDIAN_AGE";
    private static final String HERITAGE = "WORLD_HERITAGE_SITES_COUNT";
    private static final String DOMAIN = "WIKIDATA_TOP_LEVEL_DOMAIN";
    private static final String BORDERS = "BORDER_COUNTRIES";
    private static final String WORLD_BANK = "WORLD_BANK";
    private static final String CIA_FACTBOOK = "CIA_FACTBOOK";
    private static final String DOMAIN_PREFIX = ".";
    private static final Pattern DOMAIN_SEPARATOR = Pattern.compile("[,\\s]+");
    private static final Pattern CURRENCY_IN_TEXT = Pattern.compile("\\(([A-Z]{3})\\)");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern BORDER_ENTRY = Pattern.compile("([A-Z][^;:\\d()]*?)\\s*(?:\\([^)]*\\))?\\s*[\\d,.]+\\s*km");
    private static final double MAX_LIFE_EXPECTANCY_GAP = 2.0;
    private static final int MIN_TOURIST_YEAR = 2015;

    public Map<String, QuizCountryProfile> profiles(
            List<CountryCatalogEntry> entries,
            Map<String, ValidatedCountry> validated,
            Map<String, List<CatalogStatistic>> statistics,
            Map<String, Integer> tiers) {
        return entries.stream().collect(Collectors.toMap(CountryCatalogEntry::iso, entry -> {
            List<CatalogStatistic> rows = statistics.getOrDefault(entry.iso(), List.of());
            CountryFactSheet facts = validated.get(entry.iso()).facts();
            return new QuizCountryProfile(entry, facts, tiers.get(entry.iso()), drivingSide(rows), currency(entry, rows),
                    coastal(entry, rows), metrics(facts, rows), domain(entry, rows), borderCount(entry, rows));
        }));
    }

    private Map<QuizMetric, Double> metrics(CountryFactSheet facts, List<CatalogStatistic> rows) {
        Map<QuizMetric, Double> metrics = new EnumMap<>(QuizMetric.class);
        Optional.ofNullable(facts.population()).ifPresent(population -> metrics.put(QuizMetric.POPULATION, (double) population.value()));
        Optional.ofNullable(facts.areaKm2()).ifPresent(area -> metrics.put(QuizMetric.AREA, area.value()));
        Optional.ofNullable(facts.highestPoint()).ifPresent(peak -> metrics.put(QuizMetric.PEAK, (double) peak.elevationM()));
        value(rows, COASTLINE, CIA_FACTBOOK).filter(length -> length > 0).ifPresent(length -> metrics.put(QuizMetric.COASTLINE, length));
        rows.stream()
                .filter(row -> TOURISTS.equals(row.indicator()) && row.value() != null && row.value() > 0)
                .filter(row -> row.year() != null && row.year() >= MIN_TOURIST_YEAR)
                .findFirst()
                .ifPresent(row -> metrics.put(QuizMetric.TOURISTS, row.value()));
        lifeExpectancy(rows).ifPresent(years -> metrics.put(QuizMetric.LIFE_EXPECTANCY, years));
        wealth(rows).ifPresent(dollars -> metrics.put(QuizMetric.WEALTH, dollars));
        value(rows, HERITAGE, CIA_FACTBOOK).filter(count -> count > 0).ifPresent(count -> metrics.put(QuizMetric.HERITAGE, count));
        value(rows, FOREST, CIA_FACTBOOK).ifPresent(share -> metrics.put(QuizMetric.FOREST, share));
        value(rows, MEDIAN_AGE, CIA_FACTBOOK).filter(age -> age > 0).ifPresent(age -> metrics.put(QuizMetric.MEDIAN_AGE, age));
        return metrics;
    }

    private Optional<Double> lifeExpectancy(List<CatalogStatistic> rows) {
        Optional<Double> owid = value(rows, LIFE_EXPECTANCY_OWID, null);
        Optional<Double> worldBank = value(rows, LIFE_EXPECTANCY, WORLD_BANK);
        return owid.flatMap(first -> worldBank
                .filter(second -> Math.abs(first - second) <= MAX_LIFE_EXPECTANCY_GAP)
                .map(second -> (first + second) / 2));
    }

    private Optional<Double> wealth(List<CatalogStatistic> rows) {
        return value(rows, GDP_PER_CAPITA, WORLD_BANK).filter(dollars -> dollars > 0);
    }

    private String domain(CountryCatalogEntry entry, List<CatalogStatistic> rows) {
        String expected = DOMAIN_PREFIX + entry.iso().toLowerCase(Locale.ROOT);
        return text(rows, DOMAIN)
                .map(value -> DOMAIN_SEPARATOR.split(value.toLowerCase(Locale.ROOT))[0])
                .filter(expected::equals)
                .orElse(null);
    }

    private Integer borderCount(CountryCatalogEntry entry, List<CatalogStatistic> rows) {
        int ours = Optional.ofNullable(entry.neighbours()).map(List::size).orElse(0);
        if (ours == 0) {
            return null;
        }
        return text(rows, BORDERS)
                .map(BORDER_ENTRY::matcher)
                .map(matcher -> matcher.results().map(result -> result.group(1).trim()).distinct().count())
                .filter(count -> count == ours)
                .map(count -> ours)
                .orElse(null);
    }

    private String drivingSide(List<CatalogStatistic> rows) {
        return text(rows, DRIVING_SIDE)
                .map(side -> side.toUpperCase(Locale.ROOT))
                .filter(side -> LEFT.equals(side) || RIGHT.equals(side))
                .orElse(null);
    }

    private CountryCurrency currency(CountryCatalogEntry entry, List<CatalogStatistic> rows) {
        Optional<String> geonames = text(rows, CURRENCY_CODE)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .filter(code -> CURRENCY.matcher(code).matches());
        Optional<String> factbook = text(rows, CURRENCY_TEXT)
                .map(CURRENCY_IN_TEXT::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1));
        if (geonames.isPresent() && factbook.isPresent() && !geonames.equals(factbook)) {
            return null;
        }
        return geonames.or(() -> factbook)
                .flatMap(code -> entry.reference().currencies().stream()
                        .filter(currency -> currency.code().equals(code))
                        .findFirst())
                .orElse(null);
    }

    private boolean coastal(CountryCatalogEntry entry, List<CatalogStatistic> rows) {
        return !entry.reference().landlocked() && value(rows, COASTLINE, null).filter(length -> length > 0).isPresent();
    }

    private Optional<Double> value(List<CatalogStatistic> rows, String indicator, String source) {
        return rows.stream()
                .filter(row -> indicator.equals(row.indicator()) && (source == null || source.equals(row.source())))
                .map(CatalogStatistic::value)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private Optional<String> text(List<CatalogStatistic> rows, String indicator) {
        return rows.stream()
                .filter(row -> indicator.equals(row.indicator()))
                .map(CatalogStatistic::text)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .findFirst();
    }
}
