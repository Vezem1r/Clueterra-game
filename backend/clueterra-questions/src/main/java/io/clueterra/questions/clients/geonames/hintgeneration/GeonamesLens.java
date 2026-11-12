package io.clueterra.questions.clients.geonames.hintgeneration;

import io.clueterra.questions.clients.common.AbstractCachedLens;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class GeonamesLens extends AbstractCachedLens {

    private static final Set<String> OWNED_INDICATORS = Set.of(
            "GEO_CAPITAL",
            "GEO_CONTINENT",
            "GEO_LANGUAGE_COUNT",
            "GEO_PRIMARY_LANGUAGE",
            "GEO_LARGEST_CITY",
            "GEO_NEIGHBOUR_COUNT",
            "GEO_NEIGHBOURS",
            "GEO_ISLAND_NATION",
            "GEO_PHONE_PREFIX",
            "GEO_TLD",
            "GEO_AREA"
    );

    private final StatisticRepository statisticRepository;
    private final DynamicDifficultyCalculator difficultyCalculator;
    private final GeonamesAmbiguityCalculator ambiguityCalculator;
    private final AtomicReference<Map<Long, List<CountryStatistic>>> cachedByCountry = new AtomicReference<>(Map.of());

    public GeonamesLens(StatisticRepository statisticRepository,
                        DynamicDifficultyCalculator difficultyCalculator,
                        GeonamesAmbiguityCalculator ambiguityCalculator) {
        this.statisticRepository = statisticRepository;
        this.difficultyCalculator = difficultyCalculator;
        this.ambiguityCalculator = ambiguityCalculator;
    }

    @Override
    public String name() {
        return "geonames";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("GEONAMES_");
    }

    @Override
    public Set<String> claimedIndicators() {
        return OWNED_INDICATORS;
    }

    @Override
    public Set<DataSource> claimedSources() {
        return Set.of(DataSource.GEONAMES);
    }

    @Override
    public boolean canApply(StatisticContext ctx) {
        return ctx.statistic().getSource() == DataSource.GEONAMES;
    }

    @Override
    protected void loadCache() {
        List<CountryStatistic> all = statisticRepository.findBySource(DataSource.GEONAMES);
        Map<Long, List<CountryStatistic>> byCountry = new HashMap<>();
        for (CountryStatistic stat : all) {
            byCountry.computeIfAbsent(stat.getCountryId(), k -> new ArrayList<>()).add(stat);
        }
        cachedByCountry.set(byCountry);
        log.info("[Geonames] Cache refreshed: {} countries", byCountry.size());
    }

    @Override
    protected List<CandidateHint> doGenerate(StatisticContext ctx) {
        List<CandidateHint> hints = new ArrayList<>();
        switch (ctx.statistic().getIndicatorCode()) {
            case "GEO_LARGEST_CITY" -> hints.addAll(generateLargestCityHints(ctx));
            case "GEO_PRIMARY_LANGUAGE" -> hints.addAll(generatePrimaryLanguageHints(ctx));
            case "GEO_LANGUAGE_COUNT" -> hints.addAll(generateLanguageCountHints(ctx));
            case "GEO_NEIGHBOURS" -> hints.addAll(generateNeighbourListHints(ctx));
            case "GEO_NEIGHBOUR_COUNT" -> hints.addAll(generateNeighbourCountHints(ctx));
            case "GEO_ISLAND_NATION" -> hints.addAll(generateIslandHints(ctx));
            case "GEO_AREA" -> hints.addAll(generateSpecializedHints(ctx));
            default -> {
                //ignore
            }
        }
        return hints;
    }

    private Map<Long, List<CountryStatistic>> byCountry() {
        return cachedByCountry.get();
    }

    private List<CandidateHint> generateLargestCityHints(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String city = ctx.statistic().getValueText().trim();
        if (!StringUtils.hasText(city)) {
            return List.of();
        }

        int ambiguity = ambiguityCalculator.computeLargestCity();
        return buildHint(ctx, "GEONAMES_LARGEST_CITY_", "largest-city",
                Map.of("city", city), HintType.ABSOLUTE_FACT, ambiguity, 12, BigDecimal.valueOf(0.90));
    }

    private List<CandidateHint> generatePrimaryLanguageHints(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String language = normalizeLanguage(ctx.statistic().getValueText());
        if (!StringUtils.hasText(language)) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeLanguage(
                ctx.statistic().getValueText(), byCountry(), countryId);
        if (ambiguity > 12) {
            return List.of();
        }

        return buildHint(ctx, "GEONAMES_PRIMARY_LANGUAGE_", "primary-language",
                Map.of("language", language), HintType.CULTURAL, ambiguity, 20, BigDecimal.valueOf(0.84));
    }

    private List<CandidateHint> generateLanguageCountHints(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        int count = ctx.statistic().getValue().intValue();
        if (count < 4) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeLanguageCount(count, byCountry(), countryId);
        return buildHint(ctx, "GEONAMES_LANGUAGE_STACK_", "language-stack",
                Map.of("count", count), HintType.CULTURAL, ambiguity, 18, BigDecimal.valueOf(0.86));
    }

    private List<CandidateHint> generateNeighbourListHints(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        List<String> neighbours = Arrays.stream(ctx.statistic().getValueText().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        Long countryId = ctx.statistic().getCountryId();

        if (neighbours.size() == 1) {
            int ambiguity = ambiguityCalculator.computeSoloNeighbour(byCountry(), countryId);
            return buildHint(ctx, "GEONAMES_SOLO_NEIGHBOUR_", "solo-neighbour",
                    Map.of("neighbor", neighbours.getFirst()), HintType.NEIGHBOR, ambiguity, 14, BigDecimal.valueOf(0.92));
        }
        if (neighbours.size() == 2) {
            int ambiguity = ambiguityCalculator.computeTwoNeighbours(byCountry(), countryId);
            return buildHint(ctx, "GEONAMES_TWO_NEIGHBOURS_", "two-neighbours",
                    Map.of("first_neighbor", neighbours.get(0), "second_neighbor", neighbours.get(1)),
                    HintType.NEIGHBOR, ambiguity, 10, BigDecimal.valueOf(0.95));
        }

        return List.of();
    }

    private List<CandidateHint> generateNeighbourCountHints(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        int count = ctx.statistic().getValue().intValue();
        if (count < 7) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeNeighbourCountHigh(count, byCountry(), countryId);
        return buildHint(ctx, "GEONAMES_NEIGHBOUR_COUNT_HIGH_", "neighbour-count-high",
                Map.of("count", count), HintType.NEIGHBOR, ambiguity, 18, BigDecimal.valueOf(0.84));
    }

    private List<CandidateHint> generateIslandHints(StatisticContext ctx) {
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeIslandNation(byCountry(), countryId);
        return buildHint(ctx, "GEONAMES_ISLAND_NATION_", "island-nation",
                Map.of(), HintType.NEIGHBOR, ambiguity, 18, BigDecimal.valueOf(0.78));
    }

    private List<CandidateHint> generateSpecializedHints(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        List<CountryStatistic> countryStats = byCountry().getOrDefault(countryId, List.of());
        Map<String, BigDecimal> numericMap = buildNumericMap(countryStats);

        List<CandidateHint> hints = new ArrayList<>();
        hints.addAll(generateAreaScaleHints(ctx, countryId));
        hints.addAll(generateTerrainHints(ctx, countryId, numericMap));
        hints.addAll(generateUrbanHints(ctx, countryId, numericMap));
        hints.addAll(generateComboHints(ctx, countryId, numericMap));
        return hints;
    }

    private List<CandidateHint> generateAreaScaleHints(StatisticContext ctx, Long countryId) {
        double area = ctx.statistic().getValue().doubleValue();

        int areaAmbiguity = ambiguityCalculator.computeAreaScale(area, byCountry(), countryId);
        String areaComparison = describeAreaScale(area);
        return buildHint(ctx, "GEONAMES_AREA_SCALE_", "area-scale",
                Map.of("area", formatArea(area), "comparison", areaComparison),
                HintType.ABSOLUTE_FACT, areaAmbiguity, 18, BigDecimal.valueOf(0.82));
    }

    private List<CandidateHint> generateTerrainHints(StatisticContext ctx,
                                                     Long countryId,
                                                     Map<String, BigDecimal> numericMap) {
        List<CandidateHint> hints = new ArrayList<>();

        BigDecimal mountainCount = numericMap.get("GEO_MOUNTAIN_COUNT");
        if (mountainCount != null && mountainCount.intValue() > 500) {
            int count = mountainCount.intValue();
            int ambiguity = ambiguityCalculator.computeCountBand("GEO_MOUNTAIN_COUNT", count, byCountry(), countryId);
            hints.addAll(buildHint(ctx, "GEONAMES_MOUNTAIN_RICH_", "mountain-rich",
                    Map.of("count", formatNumber(count)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.82)));
        }

        BigDecimal lakeCount = numericMap.get("GEO_LAKE_COUNT");
        if (lakeCount != null && lakeCount.intValue() >= 10_000) {
            int count = lakeCount.intValue();
            int ambiguity = ambiguityCalculator.computeCountBand("GEO_LAKE_COUNT", count, byCountry(), countryId);
            hints.addAll(buildHint(ctx, "GEONAMES_LAKE_LAND_", "lake-land",
                    Map.of("count", formatNumber(count)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.80)));
        }

        BigDecimal riverCount = numericMap.get("GEO_RIVER_COUNT");
        if (riverCount != null && riverCount.intValue() > 200) {
            int count = riverCount.intValue();
            int ambiguity = ambiguityCalculator.computeCountBand("GEO_RIVER_COUNT", count, byCountry(), countryId);
            hints.addAll(buildHint(ctx, "GEONAMES_RIVER_NETWORK_", "river-network",
                    Map.of("count", formatNumber(count)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.78)));
        }

        BigDecimal islandCount = numericMap.get("GEO_ISLAND_COUNT");
        if (islandCount != null && islandCount.intValue() > 50) {
            int count = islandCount.intValue();
            int ambiguity = ambiguityCalculator.computeCountBand("GEO_ISLAND_COUNT", count, byCountry(), countryId);
            hints.addAll(buildHint(ctx, "GEONAMES_ISLAND_ARCHIPELAGO_", "island-archipelago",
                    Map.of("count", formatNumber(count)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.82)));
        }

        BigDecimal elevation = numericMap.get("GEO_HIGHEST_ELEVATION");
        if (elevation != null) {
            int elev = elevation.intValue();
            int ambiguity = ambiguityCalculator.computeElevationBand(elev, byCountry(), countryId);
            hints.addAll(buildHint(ctx, "GEONAMES_PEAK_ALTITUDE_", "peak-altitude",
                    Map.of("elevation", formatNumber(elev)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.80)));
        }

        return hints;
    }

    private List<CandidateHint> generateUrbanHints(StatisticContext ctx,
                                                   Long countryId,
                                                   Map<String, BigDecimal> numericMap) {
        List<CandidateHint> hints = new ArrayList<>();

        BigDecimal largestPop = numericMap.get("GEO_LARGEST_CITY_POP");
        if (largestPop != null && largestPop.longValue() > 5_000_000) {
            long pop = largestPop.longValue();
            int ambiguity = ambiguityCalculator.computePopulationBand(pop, byCountry(), countryId);
            hints.addAll(buildHint(ctx, "GEONAMES_MEGACITY_", "megacity",
                    Map.of("pop", formatPopulation(pop)),
                    HintType.ABSOLUTE_FACT, ambiguity, 16, BigDecimal.valueOf(0.84)));
        }

        BigDecimal primacy = numericMap.get("GEO_CITY_PRIMACY");
        if (primacy != null && primacy.doubleValue() > 5) {
            double ratio = primacy.doubleValue();
            int ambiguity = ambiguityCalculator.computePrimacy(ratio, byCountry(), countryId);
            String ratioStr = String.format(Locale.US, "%.1f", ratio);
            hints.addAll(buildHint(ctx, "GEONAMES_PRIMATE_CITY_", "primate-city",
                    Map.of("ratio", ratioStr),
                    HintType.ABSOLUTE_FACT, ambiguity, 18, BigDecimal.valueOf(0.82)));
        }

        BigDecimal cityCount = numericMap.get("GEO_CITY_COUNT");
        if (cityCount != null && cityCount.intValue() > 10_000) {
            int count = cityCount.intValue();
            int ambiguity = ambiguityCalculator.computeCountBand("GEO_CITY_COUNT", count, byCountry(), countryId);
            hints.addAll(buildHint(ctx, "GEONAMES_URBAN_SPREAD_", "urban-spread",
                    Map.of("count", formatNumber(count)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.76)));
        }

        BigDecimal airportCount = numericMap.get("GEO_AIRPORT_COUNT");
        if (airportCount != null && airportCount.intValue() > 50) {
            int count = airportCount.intValue();
            int ambiguity = ambiguityCalculator.computeCountBand("GEO_AIRPORT_COUNT", count, byCountry(), countryId);
            hints.addAll(buildHint(ctx, "GEONAMES_AIRPORT_HUB_", "airport-hub",
                    Map.of("count", formatNumber(count)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.78)));
        }

        return hints;
    }

    private List<CandidateHint> generateComboHints(StatisticContext ctx,
                                                   Long countryId,
                                                   Map<String, BigDecimal> numericMap) {
        List<CandidateHint> hints = new ArrayList<>();

        BigDecimal mountainCount = numericMap.get("GEO_MOUNTAIN_COUNT");
        BigDecimal lakeCount = numericMap.get("GEO_LAKE_COUNT");
        BigDecimal riverCount = numericMap.get("GEO_RIVER_COUNT");

        if (mountainCount != null && lakeCount != null && riverCount != null
                && mountainCount.intValue() > 200 && lakeCount.intValue() > 50 && riverCount.intValue() > 100) {
            int mAmb = ambiguityCalculator.computeCountBand("GEO_MOUNTAIN_COUNT", mountainCount.intValue(), byCountry(), countryId);
            int lAmb = ambiguityCalculator.computeCountBand("GEO_LAKE_COUNT", lakeCount.intValue(), byCountry(), countryId);
            int rAmb = ambiguityCalculator.computeCountBand("GEO_RIVER_COUNT", riverCount.intValue(), byCountry(), countryId);
            int comboAmbiguity = ambiguityCalculator.computeCombo(mAmb, lAmb, rAmb);
            hints.addAll(buildHint(ctx, "GEONAMES_WILDERNESS_PROFILE_", "wilderness-profile",
                    Map.of("mountains", formatNumber(mountainCount.intValue()),
                            "lakes", formatNumber(lakeCount.intValue()),
                            "rivers", formatNumber(riverCount.intValue())),
                    HintType.ABSOLUTE_FACT, comboAmbiguity, 18, BigDecimal.valueOf(0.86)));
        }

        BigDecimal islandCount = numericMap.get("GEO_ISLAND_COUNT");
        if (islandCount != null && lakeCount != null
                && islandCount.intValue() > 20 && lakeCount.intValue() > 50) {
            int iAmb = ambiguityCalculator.computeCountBand("GEO_ISLAND_COUNT", islandCount.intValue(), byCountry(), countryId);
            int lAmb = ambiguityCalculator.computeCountBand("GEO_LAKE_COUNT", lakeCount.intValue(), byCountry(), countryId);
            int comboAmbiguity = ambiguityCalculator.computeCombo(iAmb, lAmb);
            hints.addAll(buildHint(ctx, "GEONAMES_MARITIME_PROFILE_", "maritime-profile",
                    Map.of("islands", formatNumber(islandCount.intValue()),
                            "lakes", formatNumber(lakeCount.intValue())),
                    HintType.ABSOLUTE_FACT, comboAmbiguity, 18, BigDecimal.valueOf(0.84)));
        }

        BigDecimal density = numericMap.get("EN.POP.DNST");
        BigDecimal cityCount = numericMap.get("GEO_CITY_COUNT");
        BigDecimal largestPop = numericMap.get("GEO_LARGEST_CITY_POP");
        if (density != null && cityCount != null && largestPop != null
                && density.doubleValue() > 300 && cityCount.intValue() > 5000 && largestPop.longValue() > 1_000_000) {
            int dAmb = ambiguityCalculator.computeDensityBand(density.doubleValue(), byCountry(), countryId);
            int cAmb = ambiguityCalculator.computeCountBand("GEO_CITY_COUNT", cityCount.intValue(), byCountry(), countryId);
            int pAmb = ambiguityCalculator.computePopulationBand(largestPop.longValue(), byCountry(), countryId);
            int comboAmbiguity = ambiguityCalculator.computeCombo(dAmb, cAmb, pAmb);
            hints.addAll(buildHint(ctx, "GEONAMES_DENSE_URBAN_PROFILE_", "dense-urban-profile",
                    Map.of("density", String.format(Locale.US, "%,.0f", density.doubleValue()),
                            "cities", formatNumber(cityCount.intValue()),
                            "pop", formatPopulation(largestPop.longValue())),
                    HintType.ABSOLUTE_FACT, comboAmbiguity, 16, BigDecimal.valueOf(0.86)));
        }

        return hints;
    }

    private Map<String, BigDecimal> buildNumericMap(List<CountryStatistic> stats) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (CountryStatistic stat : stats) {
            if (stat.getValue() != null) {
                map.put(stat.getIndicatorCode(), stat.getValue());
            }
        }
        return map;
    }

    private String normalizeLanguage(String rawLanguageCode) {
        String normalized = rawLanguageCode == null ? null : rawLanguageCode.trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        String baseCode = normalized
                .replace('_', '-')
                .split("-", 2)[0]
                .toLowerCase(Locale.ENGLISH);

        if (!StringUtils.hasText(baseCode) || baseCode.length() < 2) {
            return null;
        }

        String display = Locale.of(baseCode).getDisplayLanguage(Locale.ENGLISH);
        if (!StringUtils.hasText(display) || display.equals(baseCode)) {
            return null;
        }
        return display;
    }

    private String describeAreaScale(double areaKm2) {
        if (areaKm2 < 100) return "barely a dot on the map, smaller than most cities";
        if (areaKm2 < 1_000) return "pocket-sized, about the area of a major metropolis";
        if (areaKm2 < 5_000) return "smaller than a single US county";
        if (areaKm2 < 30_000) return "roughly the size of Belgium";
        if (areaKm2 < 100_000) return "comparable to South Korea or Hungary";
        if (areaKm2 < 300_000) return "in the range of Italy or the Philippines";
        if (areaKm2 < 700_000) return "on par with France or Texas";
        if (areaKm2 < 1_500_000) return "rivaling Colombia or South Africa";
        if (areaKm2 < 4_000_000) return "in the league of India or Argentina";
        if (areaKm2 < 8_000_000) return "approaching the size of Australia or Brazil";
        return "one of the absolute giants, comparable to Russia or Canada";
    }

    private String formatArea(double areaKm2) {
        if (areaKm2 < 1) {
            return "under 1 km²";
        }
        if (areaKm2 < 10) {
            return String.format(Locale.US, "%.1f km²", areaKm2);
        }
        if (areaKm2 >= 1_000_000) {
            return String.format(Locale.US, "%.1f million km²", areaKm2 / 1_000_000.0);
        }
        return String.format(Locale.US, "%,.0f km²", areaKm2);
    }

    private String formatNumber(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    private String formatPopulation(long pop) {
        if (pop >= 1_000_000_000) {
            return String.format(Locale.US, "%.1f billion", pop / 1_000_000_000.0);
        }
        if (pop >= 1_000_000) {
            return String.format(Locale.US, "%.1f million", pop / 1_000_000.0);
        }
        return String.format(Locale.US, "%,d", pop);
    }

    private List<CandidateHint> buildHint(StatisticContext ctx,
                                          String templateBaseCode,
                                          String salt,
                                          Map<String, ?> placeholders,
                                          HintType hintType,
                                          int ambiguity,
                                          int defaultDifficulty,
                                          BigDecimal defaultQuality) {
        String templateCode = templateBaseCode + pickVariant(ctx, salt);
        return buildDynamicTemplatedCandidate(
                difficultyCalculator,
                templateCode,
                placeholders,
                hintType,
                ctx.statistic().getCategory(),
                DataSource.GEONAMES,
                ctx.statistic().getYear(),
                ambiguity,
                defaultDifficulty,
                defaultQuality,
                name()
        ).map(List::of).orElseGet(List::of);
    }

    private int pickVariant(StatisticContext ctx, String salt) {
        String seed = (ctx.countryCode() != null ? ctx.countryCode() : "XX") + "|" + salt;
        return Math.floorMod(seed.hashCode(), 3) + 1;
    }
}
