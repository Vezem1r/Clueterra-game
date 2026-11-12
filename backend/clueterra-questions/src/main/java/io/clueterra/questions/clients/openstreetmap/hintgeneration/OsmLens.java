package io.clueterra.questions.clients.openstreetmap.hintgeneration;

import java.time.Clock;
import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.clients.openstreetmap.dto.OsmCachedData;
import io.clueterra.questions.clients.openstreetmap.dto.OsmIndicatorDef;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfig;
import io.clueterra.questions.service.hint.IndicatorTemplateRenderer;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class OsmLens implements HintLens {

    private static final String TRIGGER = "OSM_MUSEUM";

    private final StatisticRepository statisticRepository;
    private final DynamicDifficultyCalculator difficultyCalculator;
    private final OsmAmbiguityCalculator ambiguityCalculator;
    private final Clock clock;

    private final AtomicReference<OsmCachedData> cache = new AtomicReference<>();

    private static final List<OsmIndicatorDef> INDICATOR_DEFS = List.of(
            new OsmIndicatorDef("OSM_PEAK", "OSM_PEAK_RANK", null, Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_VOLCANO", "OSM_VOLCANO_RANK", null, Category.GEOGRAPHY, HintType.UNIQUE_FEATURE, 1, 10, true),
            new OsmIndicatorDef("OSM_GLACIER", "OSM_GLACIER_RANK", null, Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, 1, 10, true),
            new OsmIndicatorDef("OSM_CAVE", "OSM_CAVE_RANK", null, Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_BEACH", "OSM_BEACH_RANK", null, Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_WATERFALL", "OSM_WATERFALL_RANK", null, Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_GEYSER", null, null, Category.GEOGRAPHY, HintType.UNIQUE_FEATURE, 1, 0, true),
            new OsmIndicatorDef("OSM_HOT_SPRING", "OSM_HOT_SPRING_RANK", null, Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, 1, 15, true),
            new OsmIndicatorDef("OSM_SADDLE", null, null, Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, 10, 0, false),

            new OsmIndicatorDef("OSM_CASTLE", "OSM_CASTLE_RANK", "OSM_CASTLE_SUPERLATIVE", Category.CULTURE, HintType.CULTURAL, 1, 10, true),
            new OsmIndicatorDef("OSM_FORT", "OSM_FORT_RANK", null, Category.CULTURE, HintType.CULTURAL, 1, 15, false),
            new OsmIndicatorDef("OSM_MONUMENT", "OSM_MONUMENT_RANK", null, Category.CULTURE, HintType.CULTURAL, 1, 15, false),
            new OsmIndicatorDef("OSM_WINDMILL", "OSM_WINDMILL_RANK", null, Category.CULTURE, HintType.CULTURAL, 1, 10, true),

            new OsmIndicatorDef("OSM_MUSEUM", "OSM_MUSEUM_RANK", "OSM_MUSEUM_SUPERLATIVE", Category.CULTURE, HintType.CULTURAL, 1, 10, true),
            new OsmIndicatorDef("OSM_AQUARIUM", null, null, Category.CULTURE, HintType.ABSOLUTE_FACT, 1, 0, false),
            new OsmIndicatorDef("OSM_STADIUM", "OSM_STADIUM_RANK", null, Category.CULTURE, HintType.ABSOLUTE_FACT, 1, 10, true),
            new OsmIndicatorDef("OSM_GOLF", "OSM_GOLF_RANK", null, Category.CULTURE, HintType.ABSOLUTE_FACT, 10, 15, false),
            new OsmIndicatorDef("OSM_SKI_RESORT", "OSM_SKI_RESORT_RANK", null, Category.CULTURE, HintType.CULTURAL, 1, 10, true),
            new OsmIndicatorDef("OSM_ICE_RINK", "OSM_ICE_RINK_RANK", null, Category.CULTURE, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_VIEWPOINT", "OSM_VIEWPOINT_RANK", null, Category.CULTURE, HintType.ABSOLUTE_FACT, 5, 15, false),

            new OsmIndicatorDef("OSM_MOSQUE", "OSM_MOSQUE_RANK", null, Category.CULTURE, HintType.CULTURAL, 1, 10, true),
            new OsmIndicatorDef("OSM_BUDDHIST", "OSM_BUDDHIST_RANK", null, Category.CULTURE, HintType.CULTURAL, 1, 10, true),
            new OsmIndicatorDef("OSM_HINDU", "OSM_HINDU_RANK", null, Category.CULTURE, HintType.CULTURAL, 1, 10, true),
            new OsmIndicatorDef("OSM_SIKH", null, null, Category.CULTURE, HintType.CULTURAL, 1, 0, true),
            new OsmIndicatorDef("OSM_SYNAGOGUE", null, null, Category.CULTURE, HintType.CULTURAL, 1, 0, true),

            new OsmIndicatorDef("OSM_LIGHTHOUSE", "OSM_LIGHTHOUSE_RANK", null, Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_FERRY", "OSM_FERRY_RANK", null, Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_CABLE_CAR", null, null, Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, 1, 0, false),
            new OsmIndicatorDef("OSM_PIER", "OSM_PIER_RANK", null, Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, 1, 15, false),

            new OsmIndicatorDef("OSM_NUCLEAR", "OSM_NUCLEAR_RANK", null, Category.ECONOMY, HintType.UNIQUE_FEATURE, 1, 5, true),
            new OsmIndicatorDef("OSM_COAL_PLANT", null, null, Category.ECONOMY, HintType.ABSOLUTE_FACT, 1, 0, false),

            new OsmIndicatorDef("OSM_EMBASSY", "OSM_EMBASSY_RANK", null, Category.POLITICS, HintType.ABSOLUTE_FACT, 1, 15, false),

            new OsmIndicatorDef("OSM_RUINS", "OSM_RUINS_RANK", null, Category.CULTURE, HintType.CULTURAL, 1, 15, false),
            new OsmIndicatorDef("OSM_ARCHAEOLOGICAL", "OSM_ARCHAEOLOGICAL_RANK", null, Category.CULTURE, HintType.CULTURAL, 1, 15, false),
            new OsmIndicatorDef("OSM_ZOO", "OSM_ZOO_RANK", null, Category.CULTURE, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_THEME_PARK", "OSM_THEME_PARK_RANK", null, Category.CULTURE, HintType.CULTURAL, 1, 15, false),
            new OsmIndicatorDef("OSM_AIRPORT", "OSM_AIRPORT_RANK", null, Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_HELIPAD", "OSM_HELIPAD_RANK", null, Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_RAILWAY_STATION", "OSM_RAILWAY_RANK", null, Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, 5, 15, false),
            new OsmIndicatorDef("OSM_DAM", "OSM_DAM_RANK", null, Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_LOCK", "OSM_LOCK_RANK", null, Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, 5, 10, false),
            new OsmIndicatorDef("OSM_WATER_TOWER", "OSM_WATER_TOWER_RANK", null, Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, 5, 10, false),
            new OsmIndicatorDef("OSM_WIND", "OSM_WIND_RANK", null, Category.ECONOMY, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_SOLAR", "OSM_SOLAR_RANK", null, Category.ECONOMY, HintType.ABSOLUTE_FACT, 1, 15, false),
            new OsmIndicatorDef("OSM_HYDRO", "OSM_HYDRO_RANK", null, Category.ECONOMY, HintType.ABSOLUTE_FACT, 1, 15, false)
    );

    private static final List<OsmComboDef> COMBO_DEFS = List.of(
            new OsmComboDef("OSM_JUSTICE_PROFILE", Category.POLITICS, HintType.ABSOLUTE_FACT, List.of("OSM_COURTHOUSE", "OSM_PRISON", "OSM_BORDER")),
            new OsmComboDef("OSM_ENERGY_PROFILE", Category.ECONOMY, HintType.ABSOLUTE_FACT, List.of("OSM_WIND", "OSM_SOLAR", "OSM_HYDRO")),
            new OsmComboDef("OSM_HISTORY_PROFILE", Category.CULTURE, HintType.CULTURAL, List.of("OSM_RUINS", "OSM_CASTLE", "OSM_ARCHAEOLOGICAL")),
            new OsmComboDef("OSM_LEISURE_PROFILE", Category.CULTURE, HintType.CULTURAL, List.of("OSM_THEME_PARK", "OSM_WATER_PARK", "OSM_ZOO")),
            new OsmComboDef("OSM_WATER_PROFILE", Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, List.of("OSM_DAM", "OSM_WATER_TOWER", "OSM_LOCK")),
            new OsmComboDef("OSM_TRANSIT_PROFILE", Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, List.of("OSM_AIRPORT", "OSM_HELIPAD", "OSM_RAILWAY_STATION")),
            new OsmComboDef("OSM_COAST_PROFILE", Category.INFRASTRUCTURE, HintType.ABSOLUTE_FACT, List.of("OSM_LIGHTHOUSE", "OSM_PIER", "OSM_FERRY")),
            new OsmComboDef("OSM_VERTICAL_PROFILE", Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, List.of("OSM_PEAK", "OSM_WATERFALL", "OSM_CAVE")),
            new OsmComboDef("OSM_HERITAGE_DEFENSE_PROFILE", Category.CULTURE, HintType.CULTURAL, List.of("OSM_CASTLE", "OSM_FORT", "OSM_MONUMENT")),
            new OsmComboDef("OSM_WINTER_PROFILE", Category.CULTURE, HintType.CULTURAL, List.of("OSM_SKI_RESORT", "OSM_ICE_RINK", "OSM_CABLE_CAR")),
            new OsmComboDef("OSM_EXTREME_NATURE_PROFILE", Category.GEOGRAPHY, HintType.UNIQUE_FEATURE, List.of("OSM_VOLCANO", "OSM_GLACIER", "OSM_HOT_SPRING", "OSM_GEYSER")),
            new OsmComboDef("OSM_SPIRITUAL_PROFILE", Category.CULTURE, HintType.CULTURAL, List.of("OSM_MOSQUE", "OSM_HINDU", "OSM_BUDDHIST", "OSM_SYNAGOGUE", "OSM_SIKH")),
            new OsmComboDef("OSM_SPORTS_ELITE_PROFILE", Category.CULTURE, HintType.ABSOLUTE_FACT, List.of("OSM_STADIUM", "OSM_GOLF")),
            new OsmComboDef("OSM_PANORAMA_PROFILE", Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, List.of("OSM_VIEWPOINT", "OSM_SADDLE", "OSM_CAVE")),
            new OsmComboDef("OSM_STATE_CULTURE_PROFILE", Category.POLITICS, HintType.ABSOLUTE_FACT, List.of("OSM_EMBASSY", "OSM_MUSEUM"))
    );

    private static final Map<String, OsmComponentLabel> COMPONENT_LABELS = Map.ofEntries(
            Map.entry("OSM_COURTHOUSE", new OsmComponentLabel("courthouse", "courthouses")),
            Map.entry("OSM_PRISON", new OsmComponentLabel("prison", "prisons")),
            Map.entry("OSM_BORDER", new OsmComponentLabel("border checkpoint", "border checkpoints")),
            Map.entry("OSM_WIND", new OsmComponentLabel("wind site", "wind sites")),
            Map.entry("OSM_SOLAR", new OsmComponentLabel("solar facility", "solar facilities")),
            Map.entry("OSM_HYDRO", new OsmComponentLabel("hydro plant", "hydro plants")),
            Map.entry("OSM_RUINS", new OsmComponentLabel("ruin", "ruins")),
            Map.entry("OSM_CASTLE", new OsmComponentLabel("castle", "castles")),
            Map.entry("OSM_ARCHAEOLOGICAL", new OsmComponentLabel("archaeological site", "archaeological sites")),
            Map.entry("OSM_THEME_PARK", new OsmComponentLabel("theme park", "theme parks")),
            Map.entry("OSM_WATER_PARK", new OsmComponentLabel("water park", "water parks")),
            Map.entry("OSM_ZOO", new OsmComponentLabel("zoo", "zoos")),
            Map.entry("OSM_DAM", new OsmComponentLabel("dam", "dams")),
            Map.entry("OSM_WATER_TOWER", new OsmComponentLabel("water tower", "water towers")),
            Map.entry("OSM_LOCK", new OsmComponentLabel("canal lock", "canal locks")),
            Map.entry("OSM_AIRPORT", new OsmComponentLabel("airport", "airports")),
            Map.entry("OSM_HELIPAD", new OsmComponentLabel("helipad", "helipads")),
            Map.entry("OSM_RAILWAY_STATION", new OsmComponentLabel("railway station", "railway stations")),
            Map.entry("OSM_LIGHTHOUSE", new OsmComponentLabel("lighthouse", "lighthouses")),
            Map.entry("OSM_PIER", new OsmComponentLabel("pier", "piers")),
            Map.entry("OSM_FERRY", new OsmComponentLabel("ferry terminal", "ferry terminals")),
            Map.entry("OSM_PEAK", new OsmComponentLabel("peak", "peaks")),
            Map.entry("OSM_WATERFALL", new OsmComponentLabel("waterfall", "waterfalls")),
            Map.entry("OSM_CAVE", new OsmComponentLabel("cave entrance", "cave entrances")),
            Map.entry("OSM_FORT", new OsmComponentLabel("fort", "forts")),
            Map.entry("OSM_MONUMENT", new OsmComponentLabel("monument", "monuments")),
            Map.entry("OSM_SKI_RESORT", new OsmComponentLabel("ski resort", "ski resorts")),
            Map.entry("OSM_ICE_RINK", new OsmComponentLabel("ice rink", "ice rinks")),
            Map.entry("OSM_CABLE_CAR", new OsmComponentLabel("cable car station", "cable car stations")),
            Map.entry("OSM_VOLCANO", new OsmComponentLabel("volcano", "volcanoes")),
            Map.entry("OSM_GLACIER", new OsmComponentLabel("glacier", "glaciers")),
            Map.entry("OSM_HOT_SPRING", new OsmComponentLabel("hot spring", "hot springs")),
            Map.entry("OSM_GEYSER", new OsmComponentLabel("geyser site", "geyser sites")),
            Map.entry("OSM_MOSQUE", new OsmComponentLabel("mosque", "mosques")),
            Map.entry("OSM_HINDU", new OsmComponentLabel("Hindu temple", "Hindu temples")),
            Map.entry("OSM_BUDDHIST", new OsmComponentLabel("Buddhist temple", "Buddhist temples")),
            Map.entry("OSM_SYNAGOGUE", new OsmComponentLabel("synagogue", "synagogues")),
            Map.entry("OSM_SIKH", new OsmComponentLabel("Gurdwara", "Gurdwaras")),
            Map.entry("OSM_STADIUM", new OsmComponentLabel("stadium", "stadiums")),
            Map.entry("OSM_GOLF", new OsmComponentLabel("golf course", "golf courses")),
            Map.entry("OSM_VIEWPOINT", new OsmComponentLabel("viewpoint", "viewpoints")),
            Map.entry("OSM_SADDLE", new OsmComponentLabel("mountain pass", "mountain passes")),
            Map.entry("OSM_EMBASSY", new OsmComponentLabel("embassy", "embassies")),
            Map.entry("OSM_MUSEUM", new OsmComponentLabel("museum", "museums")),
            Map.entry("OSM_WINDMILL", new OsmComponentLabel("historic windmill", "historic windmills")),
            Map.entry("OSM_NUCLEAR", new OsmComponentLabel("nuclear plant", "nuclear plants")),
            Map.entry("OSM_AQUARIUM", new OsmComponentLabel("public aquarium", "public aquariums")),
            Map.entry("OSM_COAL_PLANT", new OsmComponentLabel("coal plant", "coal plants")),
            Map.entry("OSM_BEACH", new OsmComponentLabel("beach", "beaches"))
    );

    private static final Set<String> NAMED_FEATURE_CODES = Set.of(
            "OSM_HIGHEST_PEAK", "OSM_HIGHEST_VOLCANO", "OSM_LARGEST_LAKE");

    @Override
    public String name() {
        return "osm";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("OSM_");
    }

    @Override
    public boolean canApply(StatisticContext ctx) {
        return TRIGGER.equals(ctx.statistic().getIndicatorCode());
    }

    @Override
    public void invalidateCache() {
        cache.set(null);
    }

    @Override
    public List<CandidateHint> generate(StatisticContext ctx) {
        OsmCachedData data = refreshCacheIfNeeded();
        Long countryId = ctx.statistic().getCountryId();
        List<CountryStatistic> countryStats = data.byCountry().getOrDefault(countryId, List.of());
        if (countryStats.isEmpty()) return List.of();

        Set<String> comboConsumed = collectComboConsumedIndicators(countryStats);

        List<CandidateHint> hints = new ArrayList<>();
        for (OsmIndicatorDef def : INDICATOR_DEFS) {
            generateIndicatorHints(countryStats, def, data, countryId, ctx, hints, comboConsumed);
        }
        generateNamedFeatureHints(countryStats, ctx, hints);
        generateComboHints(countryStats, data, ctx, hints);
        return hints;
    }

    private Set<String> collectComboConsumedIndicators(List<CountryStatistic> countryStats) {
        Set<String> consumed = new HashSet<>();
        for (OsmComboDef comboDef : COMBO_DEFS) {
            Map<String, Long> values = collectPositiveValues(countryStats, comboDef.components());
            if (isComboEligible(comboDef.code(), values)) {
                consumed.addAll(comboDef.components());
            }
        }
        return consumed;
    }

    private void generateIndicatorHints(
            List<CountryStatistic> countryStats,
            OsmIndicatorDef def,
            OsmCachedData data,
            Long countryId,
            StatisticContext ctx,
            List<CandidateHint> hints,
            Set<String> comboConsumed) {

        BigDecimal valueBd = findValue(countryStats, def.code());
        if (valueBd == null) return;

        long value = valueBd.longValue();
        if (value < def.minValue()) return;

        Map<Long, Integer> ranks = data.ranksByIndicator().getOrDefault(def.code(), Map.of());
        int rank = ranks.getOrDefault(countryId, Integer.MAX_VALUE);
        int totalWithData = ranks.size();

        if (def.generateMainHint() && !comboConsumed.contains(def.code())) {
            int ambiguity = ambiguityCalculator.computeDecile(valueBd, def.code(), data.byCountry());
            hints.add(buildHint(ctx, def.code(), def.category(), def.hintType(),
                    buildCountText(countryId, def.code(), value), ambiguity));
        }

        if (def.rankCode() != null && def.topNForRank() > 0 && rank <= def.topNForRank()) {
            int rankAmbiguity = ambiguityCalculator.computeRankBand(rank, totalWithData);
            hints.add(buildHint(ctx, def.rankCode(), def.category(), HintType.RANKING,
                    buildRankText(countryId, def.rankCode(), def.code(), rank), rankAmbiguity));
        }

        if (def.supCode() != null && rank == 1) {
            hints.add(buildHint(ctx, def.supCode(), def.category(), HintType.SUPERLATIVE,
                    buildSuperlativeText(countryId, def.supCode(), def.code()), 1));
        }
    }

    private void generateNamedFeatureHints(
            List<CountryStatistic> countryStats,
            StatisticContext ctx,
            List<CandidateHint> hints) {

        for (String code : NAMED_FEATURE_CODES) {
            CountryStatistic stat = findStat(countryStats, code);
            if (stat == null || StringUtils.isBlank(stat.getValueText())) continue;

            hints.add(buildHint(ctx, code, Category.GEOGRAPHY, HintType.ABSOLUTE_FACT,
                    buildNamedFeatureText(ctx.statistic().getCountryId(), code, stat.getValueText()), 1));
        }
    }

    private void generateComboHints(
            List<CountryStatistic> countryStats,
            OsmCachedData data,
            StatisticContext ctx,
            List<CandidateHint> hints) {

        for (OsmComboDef comboDef : COMBO_DEFS) {
            Map<String, Long> values = collectPositiveValues(countryStats, comboDef.components());
            if (!isComboEligible(comboDef.code(), values)) continue;

            int ambiguity = ambiguityCalculator.computeExactTuple(values, data.byCountry());
            hints.add(buildHint(ctx, comboDef.code(), comboDef.category(), comboDef.hintType(),
                    buildComboText(ctx.statistic().getCountryId(), comboDef.code(), values), ambiguity));
        }
    }

    private boolean isComboEligible(String comboCode, Map<String, Long> values) {
        long positiveCount = values.values().stream().filter(v -> v > 0).count();
        return switch (comboCode) {
            case "OSM_JUSTICE_PROFILE" -> values.getOrDefault("OSM_COURTHOUSE", 0L) > 0
                    && values.getOrDefault("OSM_PRISON", 0L) > 0;
            case "OSM_ENERGY_PROFILE", "OSM_COAST_PROFILE", "OSM_VERTICAL_PROFILE", "OSM_WINTER_PROFILE",
                 "OSM_EXTREME_NATURE_PROFILE", "OSM_WATER_PROFILE", "OSM_LEISURE_PROFILE",
                 "OSM_PANORAMA_PROFILE", "OSM_SPIRITUAL_PROFILE" -> positiveCount >= 2;
            case "OSM_SPORTS_ELITE_PROFILE" -> values.getOrDefault("OSM_STADIUM", 0L) > 0
                    && values.getOrDefault("OSM_GOLF", 0L) > 0;
            case "OSM_STATE_CULTURE_PROFILE" -> values.getOrDefault("OSM_EMBASSY", 0L) > 0
                    && values.getOrDefault("OSM_MUSEUM", 0L) > 0;
            case "OSM_HISTORY_PROFILE" -> values.getOrDefault("OSM_CASTLE", 0L) > 0
                    && values.getOrDefault("OSM_RUINS", 0L) > 0;
            case "OSM_TRANSIT_PROFILE" -> values.getOrDefault("OSM_AIRPORT", 0L) > 0
                    && values.getOrDefault("OSM_RAILWAY_STATION", 0L) > 0;
            case "OSM_HERITAGE_DEFENSE_PROFILE" -> values.getOrDefault("OSM_CASTLE", 0L) > 0 && positiveCount >= 2;
            default -> false;
        };
    }

    private Map<String, Long> collectPositiveValues(List<CountryStatistic> stats, List<String> codes) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (String code : codes) {
            BigDecimal value = findValue(stats, code);
            values.put(code, value != null ? value.longValue() : 0L);
        }
        return values;
    }

    private OsmCachedData refreshCacheIfNeeded() {
        OsmCachedData cached = cache.get();
        if (cached != null && !cached.isExpired()) return cached;

        List<CountryStatistic> all = statisticRepository.findBySource(DataSource.OPEN_STREET_MAP);
        Map<Long, List<CountryStatistic>> byCountry = new HashMap<>();
        Map<String, Map<Long, Long>> valuesByIndicator = new HashMap<>();

        for (CountryStatistic stat : all) {
            byCountry.computeIfAbsent(stat.getCountryId(), k -> new ArrayList<>()).add(stat);
            if (stat.getValue() != null && stat.getValue().longValue() > 0
                    && !NAMED_FEATURE_CODES.contains(stat.getIndicatorCode())) {
                valuesByIndicator
                        .computeIfAbsent(stat.getIndicatorCode(), k -> new HashMap<>())
                        .put(stat.getCountryId(), stat.getValue().longValue());
            }
        }

        Map<String, Map<Long, Integer>> ranksByIndicator = new HashMap<>();
        for (Map.Entry<String, Map<Long, Long>> entry : valuesByIndicator.entrySet()) {
            List<Map.Entry<Long, Long>> sorted = new ArrayList<>(entry.getValue().entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            Map<Long, Integer> ranks = new HashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                ranks.put(sorted.get(i).getKey(), i + 1);
            }
            ranksByIndicator.put(entry.getKey(), ranks);
        }

        OsmCachedData data = new OsmCachedData(byCountry, ranksByIndicator, Instant.now(clock));
        cache.set(data);
        log.info("[OSM] Cache refreshed: {} countries", byCountry.size());
        return data;
    }

    private String buildCountText(Long countryId, String indicatorCode, long value) {
        String template = pickConfiguredTemplate(countryId, indicatorCode, "OSM_COUNT_STYLE");
        OsmComponentLabel label = COMPONENT_LABELS.get(indicatorCode);
        String displayName = (label != null) ? pluralize(value, label) : normalizedDisplayName(indicatorCode);
        return resolveTemplate(template, Map.of(
                "value", String.valueOf(value),
                "value_formatted", format(value),
                "display_name", displayName
        ));
    }

    private String buildRankText(Long countryId, String rankCode, String baseCode, int rank) {
        String template = pickConfiguredTemplate(countryId, rankCode, "OSM_RANK_STYLE");
        return resolveTemplate(template, Map.of(
                "rank", String.valueOf(rank),
                "ordinal", ordinal(rank),
                "display_name", normalizedDisplayName(baseCode)
        ));
    }

    private String buildSuperlativeText(Long countryId, String supCode, String baseCode) {
        String template = pickConfiguredTemplate(countryId, supCode, "OSM_SUPERLATIVE_STYLE");
        return resolveTemplate(template, Map.of(
                "display_name", normalizedDisplayName(baseCode)
        ));
    }

    private String buildNamedFeatureText(Long countryId, String code, String valueText) {
        String template = pickConfiguredTemplate(countryId, code, "OSM_NAMED_STYLE");
        return resolveTemplate(template, Map.of(
                "value_text", valueText,
                "feature_name", normalizedDisplayName(code)
        ));
    }

    private String buildComboText(Long countryId, String comboCode, Map<String, Long> values) {
        String template = pickConfiguredTemplate(countryId, comboCode, comboCode);
        return resolveTemplate(template, comboParams(comboCode, values));
    }

    private Map<String, String> comboParams(String comboCode, Map<String, Long> values) {
        Map<String, String> params = new HashMap<>();
        addParam(params, "courthouses", values, "OSM_COURTHOUSE");
        addParam(params, "prisons", values, "OSM_PRISON");
        addParam(params, "borders", values, "OSM_BORDER");
        addParam(params, "wind", values, "OSM_WIND");
        addParam(params, "solar", values, "OSM_SOLAR");
        addParam(params, "hydro", values, "OSM_HYDRO");
        addParam(params, "ruins", values, "OSM_RUINS");
        addParam(params, "castles", values, "OSM_CASTLE");
        addParam(params, "archaeological", values, "OSM_ARCHAEOLOGICAL");
        addParam(params, "theme_parks", values, "OSM_THEME_PARK");
        addParam(params, "water_parks", values, "OSM_WATER_PARK");
        addParam(params, "zoos", values, "OSM_ZOO");
        addParam(params, "dams", values, "OSM_DAM");
        addParam(params, "water_towers", values, "OSM_WATER_TOWER");
        addParam(params, "locks", values, "OSM_LOCK");
        addParam(params, "airports", values, "OSM_AIRPORT");
        addParam(params, "helipads", values, "OSM_HELIPAD");
        addParam(params, "railway_stations", values, "OSM_RAILWAY_STATION");
        addParam(params, "mosques", values, "OSM_MOSQUE");
        addParam(params, "hindu", values, "OSM_HINDU");
        addParam(params, "buddhist", values, "OSM_BUDDHIST");
        addParam(params, "synagogues", values, "OSM_SYNAGOGUE");
        addParam(params, "sikh", values, "OSM_SIKH");
        addParam(params, "stadiums", values, "OSM_STADIUM");
        addParam(params, "golf", values, "OSM_GOLF");
        addParam(params, "viewpoint", values, "OSM_VIEWPOINT");
        addParam(params, "saddle", values, "OSM_SADDLE");
        addParam(params, "cave", values, "OSM_CAVE");
        addParam(params, "embassy", values, "OSM_EMBASSY");
        addParam(params, "museum", values, "OSM_MUSEUM");
        params.put("profile_name", IndicatorConfig.getDisplayName(comboCode, comboCode).toLowerCase(Locale.ENGLISH));
        params.put("components", formatComponents(values, false));
        params.put("components_with_zero", formatComponents(values, true));
        params.put("zero_features", formatZeroFeatures(values));
        params.put("nonzero_components", String.valueOf(values.values().stream().filter(v -> v > 0).count()));
        return params;
    }

    private void addParam(Map<String, String> params, String name, Map<String, Long> values, String osmCode) {
        long count = values.getOrDefault(osmCode, 0L);
        OsmComponentLabel label = COMPONENT_LABELS.get(osmCode);
        if (label == null) {
            params.put(name, format(count));
            return;
        }
        if (count == 0) {
            params.put(name, "no " + label.plural());
        } else {
            params.put(name, format(count) + " " + pluralize(count, label));
        }
    }

    private String pickConfiguredTemplate(Long countryId, String seedCode, String templateBaseCode) {
        List<String> templateCodes = new ArrayList<>();
        templateCodes.add(templateBaseCode);
        for (int i = 1; i <= 5; i++) {
            String variantCode = templateBaseCode + "_" + i;
            if (IndicatorConfig.hasTemplate(variantCode)) {
                templateCodes.add(variantCode);
            }
        }
        int seed = String.valueOf(countryId).concat("|").concat(seedCode).hashCode();
        String selectedCode = templateCodes.get(Math.floorMod(seed, templateCodes.size()));
        return IndicatorConfig.getTemplate(selectedCode);
    }

    private String resolveTemplate(String template, Map<String, String> params) {
        return IndicatorTemplateRenderer.render(template, params);
    }

    private String formatComponents(Map<String, Long> values, boolean includeZeroLiteral) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            OsmComponentLabel label = COMPONENT_LABELS.get(entry.getKey());
            if (label == null) continue;
            long count = entry.getValue();
            if (count > 0) {
                parts.add(format(count) + " " + pluralize(count, label));
            } else if (includeZeroLiteral) {
                parts.add("literally zero " + label.plural());
            }
        }
        return joinNatural(parts);
    }

    private String formatZeroFeatures(Map<String, Long> values) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            if (entry.getValue() == 0) {
                OsmComponentLabel label = COMPONENT_LABELS.get(entry.getKey());
                if (label != null) {
                    parts.add(label.plural());
                }
            }
        }
        return joinNatural(parts);
    }

    private String joinNatural(List<String> parts) {
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        if (parts.size() == 2) return parts.get(0) + " and " + parts.get(1);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + ", and " + parts.getLast();
    }

    private String pluralize(long count, OsmComponentLabel label) {
        return count == 1 ? label.singular() : label.plural();
    }

    private CandidateHint buildHint(
            StatisticContext ctx,
            String indicatorCode,
            Category category,
            HintType type,
            String text,
            int ambiguity) {
        return TemplatedHintBuilder.buildCandidate(
                text,
                type,
                category,
                DataSource.OPEN_STREET_MAP,
                indicatorCode,
                ctx.statistic().getYear(),
                difficultyCalculator.computeDifficulty(indicatorCode, ambiguity, 30),
                difficultyCalculator.computeQuality(indicatorCode, ambiguity, BigDecimal.valueOf(0.82)),
                ambiguity,
                name(),
                null
        );
    }

    private String normalizedDisplayName(String indicatorCode) {
        String fromConfig = IndicatorConfig.getDisplayName(indicatorCode, null);
        if (fromConfig != null) {
            return fromConfig.toLowerCase(Locale.ENGLISH).trim();
        }
        OsmComponentLabel label = COMPONENT_LABELS.get(indicatorCode);
        if (label != null) {
            return label.plural();
        }
        return indicatorCode.toLowerCase(Locale.ENGLISH).replace("osm_", "").replace('_', ' ');
    }

    private static String format(long value) {
        return String.format(Locale.ENGLISH, "%,d", value);
    }

    private static BigDecimal findValue(List<CountryStatistic> stats, String code) {
        for (CountryStatistic stat : stats) {
            if (code.equals(stat.getIndicatorCode()) && stat.getValue() != null) {
                return stat.getValue();
            }
        }
        return null;
    }

    private static CountryStatistic findStat(List<CountryStatistic> stats, String code) {
        for (CountryStatistic stat : stats) {
            if (code.equals(stat.getIndicatorCode())) {
                return stat;
            }
        }
        return null;
    }

    private static String ordinal(int n) {
        if (n <= 0) return String.valueOf(n);
        int lastTwo = n % 100;
        if (lastTwo >= 11 && lastTwo <= 13) return n + "th";
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }
}
