package io.clueterra.questions.clients.geonames.hintgeneration;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfigTestSupport;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

class GeonamesLensTest {

    private StatisticRepository statisticRepository;

    private GeonamesLens lens;

    @BeforeEach
    void setUp() {
        statisticRepository = mock(StatisticRepository.class);
        DynamicDifficultyCalculator difficultyCalculator = new DynamicDifficultyCalculator();
        GeonamesAmbiguityCalculator ambiguityCalculator = new GeonamesAmbiguityCalculator();
        lens = new GeonamesLens(statisticRepository, difficultyCalculator, ambiguityCalculator);

        lenient().when(statisticRepository.findBySource(DataSource.GEONAMES)).thenReturn(List.of());

        Map<String, String> templates = new HashMap<>();
        registerAllVariants(templates, "GEONAMES_PRIMARY_LANGUAGE_", "language {language}");
        registerAllVariants(templates, "GEONAMES_SOLO_NEIGHBOUR_", "neighbor {neighbor}");
        registerAllVariants(templates, "GEONAMES_TWO_NEIGHBOURS_", "neighbors {first_neighbor} {second_neighbor}");
        registerAllVariants(templates, "GEONAMES_LANGUAGE_STACK_", "stack {count}");
        registerAllVariants(templates, "GEONAMES_ISLAND_NATION_", "island");
        registerAllVariants(templates, "GEONAMES_LARGEST_CITY_", "city {city}");
        registerAllVariants(templates, "GEONAMES_NEIGHBOUR_COUNT_HIGH_", "neighbours {count}");
        registerAllVariants(templates, "GEONAMES_AREA_SCALE_", "area {area} {comparison}");
        registerAllVariants(templates, "GEONAMES_STRETCH_", "stretch {ns_km} {ew_km}");
        registerAllVariants(templates, "GEONAMES_ELONGATED_", "elongated {longer_dim} {shorter_dim}");
        registerAllVariants(templates, "GEONAMES_COMPACT_", "compact {ns_km} {ew_km}");
        registerAllVariants(templates, "GEONAMES_MOUNTAIN_RICH_", "mountains {count}");
        registerAllVariants(templates, "GEONAMES_LAKE_LAND_", "lakes {count}");
        registerAllVariants(templates, "GEONAMES_RIVER_NETWORK_", "rivers {count}");
        registerAllVariants(templates, "GEONAMES_ISLAND_ARCHIPELAGO_", "islands {count}");
        registerAllVariants(templates, "GEONAMES_PEAK_ALTITUDE_", "peak {elevation}");
        registerAllVariants(templates, "GEONAMES_MEGACITY_", "megacity {pop}");
        registerAllVariants(templates, "GEONAMES_PRIMATE_CITY_", "primate {ratio}");
        registerAllVariants(templates, "GEONAMES_URBAN_SPREAD_", "urban {count}");
        registerAllVariants(templates, "GEONAMES_AIRPORT_HUB_", "airports {count}");
        registerAllVariants(templates, "GEONAMES_WILDERNESS_PROFILE_", "wilderness {mountains} {lakes} {rivers}");
        registerAllVariants(templates, "GEONAMES_MARITIME_PROFILE_", "maritime {islands} {lakes}");
        registerAllVariants(templates, "GEONAMES_DENSE_URBAN_PROFILE_", "dense {density} {cities} {pop}");
        IndicatorConfigTestSupport.installTemplates(templates);
    }

    @AfterEach
    void tearDown() {
        IndicatorConfigTestSupport.clear();
    }

    @Test
    void rejectsOverlyCommonPrimaryLanguage() {
        when(statisticRepository.findBySource(DataSource.GEONAMES)).thenReturn(
                buildLanguageStats("en", 20));

        List<CandidateHint> hints = lens.generate(context("GEO_PRIMARY_LANGUAGE", null, "en-US"));

        assertThat(hints).isEmpty();
    }

    @Test
    void generatesNeighborHintForSingleNeighbor() {
        List<CandidateHint> hints = lens.generate(context("GEO_NEIGHBOURS", null, "France"));

        assertThat(hints).hasSize(1);
        assertThat(hints.getFirst().text()).contains("France");
    }

    @Test
    void normalizesDistinctLanguageCodeToDisplayLanguage() {
        List<CandidateHint> hints = lens.generate(context("GEO_PRIMARY_LANGUAGE", null, "ja-JP"));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("GEONAMES_PRIMARY_LANGUAGE_");
            assertThat(hint.text()).contains("Japanese");
        });
    }

    @Test
    void generatesTwoNeighbourHintForTwoCountryList() {
        List<CandidateHint> hints = lens.generate(context("GEO_NEIGHBOURS", null, "France, Germany"));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.text()).contains("France").contains("Germany"));
    }

    @Test
    void generatesLanguageStackForHighLanguageCount() {
        List<CandidateHint> hints = lens.generate(context("GEO_LANGUAGE_COUNT", BigDecimal.valueOf(6), null));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("GEONAMES_LANGUAGE_STACK_"));
    }

    @Test
    void generatesIslandNationHint() {
        List<CandidateHint> hints = lens.generate(context("GEO_ISLAND_NATION", null, null));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("GEONAMES_ISLAND_NATION_"));
    }

    @Test
    void generatesAreaScaleHintForGeoArea() {
        List<CountryStatistic> countryStats = List.of(
                buildStat(2L, "GEO_AREA", BigDecimal.valueOf(377975), null),
                buildStat(2L, "GEO_NS_DISTANCE", BigDecimal.valueOf(2000), null),
                buildStat(2L, "GEO_EW_DISTANCE", BigDecimal.valueOf(600), null)
        );
        when(statisticRepository.findBySource(DataSource.GEONAMES)).thenReturn(countryStats);

        List<CandidateHint> hints = lens.generate(context("GEO_AREA", BigDecimal.valueOf(377975), null));

        assertThat(hints)
                .isNotEmpty()
                .anySatisfy(hint -> assertThat(hint.sourceIndicator()).startsWith("GEONAMES_AREA_SCALE_"))
                .noneSatisfy(hint -> assertThat(hint.sourceIndicator()).startsWith("GEONAMES_STRETCH_"))
                .noneSatisfy(hint -> assertThat(hint.sourceIndicator()).startsWith("GEONAMES_ELONGATED_"))
                .noneSatisfy(hint -> assertThat(hint.sourceIndicator()).startsWith("GEONAMES_COMPACT_"));
    }

    @Test
    void onlyGeneratesLakeCountForExceptionalLakeRichness() {
        when(statisticRepository.findBySource(DataSource.GEONAMES)).thenReturn(List.of(
                buildStat(2L, "GEO_AREA", BigDecimal.valueOf(338455), null),
                buildStat(2L, "GEO_LAKE_COUNT", BigDecimal.valueOf(38_379), null)));

        List<CandidateHint> hints = lens.generate(context("GEO_AREA", BigDecimal.valueOf(338455), null));

        assertThat(hints).anySatisfy(hint ->
                assertThat(hint.sourceIndicator()).startsWith("GEONAMES_LAKE_LAND_"));
    }

    @Test
    void generatesMountainRichHintForHighMountainCount() {
        List<CountryStatistic> countryStats = List.of(
                buildStat(2L, "GEO_AREA", BigDecimal.valueOf(377975), null),
                buildStat(2L, "GEO_MOUNTAIN_COUNT", BigDecimal.valueOf(800), null),
                buildStat(2L, "GEO_HIGHEST_ELEVATION", BigDecimal.valueOf(3776), null)
        );
        when(statisticRepository.findBySource(DataSource.GEONAMES)).thenReturn(countryStats);

        List<CandidateHint> hints = lens.generate(context("GEO_AREA", BigDecimal.valueOf(377975), null));

        assertThat(hints)
                .anySatisfy(hint -> assertThat(hint.sourceIndicator()).startsWith("GEONAMES_MOUNTAIN_RICH_"))
                .anySatisfy(hint -> assertThat(hint.sourceIndicator()).startsWith("GEONAMES_PEAK_ALTITUDE_"));
    }

    @Test
    void generatesMegacityHintForLargeCity() {
        List<CountryStatistic> countryStats = List.of(
                buildStat(2L, "GEO_AREA", BigDecimal.valueOf(377975), null),
                buildStat(2L, "GEO_LARGEST_CITY_POP", BigDecimal.valueOf(13_960_000), null)
        );
        when(statisticRepository.findBySource(DataSource.GEONAMES)).thenReturn(countryStats);

        List<CandidateHint> hints = lens.generate(context("GEO_AREA", BigDecimal.valueOf(377975), null));

        assertThat(hints).anySatisfy(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("GEONAMES_MEGACITY_");
            assertThat(hint.text()).contains("14.0 million");
        });
    }

    @Test
    void generatesWildernessComboForRichTerrain() {
        List<CountryStatistic> countryStats = List.of(
                buildStat(2L, "GEO_AREA", BigDecimal.valueOf(9_984_670), null),
                buildStat(2L, "GEO_MOUNTAIN_COUNT", BigDecimal.valueOf(1200), null),
                buildStat(2L, "GEO_LAKE_COUNT", BigDecimal.valueOf(500), null),
                buildStat(2L, "GEO_RIVER_COUNT", BigDecimal.valueOf(300), null)
        );
        when(statisticRepository.findBySource(DataSource.GEONAMES)).thenReturn(countryStats);

        List<CandidateHint> hints = lens.generate(context("GEO_AREA", BigDecimal.valueOf(9_984_670), null));

        assertThat(hints).anySatisfy(hint ->
                assertThat(hint.sourceIndicator()).startsWith("GEONAMES_WILDERNESS_PROFILE_"));
        assertThat(hints).noneSatisfy(hint ->
                assertThat(hint.sourceIndicator()).startsWith("GEONAMES_LAKE_LAND_"));
    }

    @Test
    void generatesNoSpecializedHintsWhenBelowThresholds() {
        List<CountryStatistic> countryStats = List.of(
                buildStat(2L, "GEO_AREA", BigDecimal.valueOf(468), null),
                buildStat(2L, "GEO_MOUNTAIN_COUNT", BigDecimal.valueOf(5), null),
                buildStat(2L, "GEO_LAKE_COUNT", BigDecimal.valueOf(2), null)
        );
        when(statisticRepository.findBySource(DataSource.GEONAMES)).thenReturn(countryStats);

        List<CandidateHint> hints = lens.generate(context("GEO_AREA", BigDecimal.valueOf(468), null));

        assertThat(hints).hasSize(1);
        assertThat(hints.getFirst().sourceIndicator()).startsWith("GEONAMES_AREA_SCALE_");
    }

    @Test
    void doesNotGenerateGenericHintsAnymore() {
        List<CandidateHint> hints = lens.generate(context("GEO_CAPITAL", null, "Tokyo"));

        assertThat(hints).isEmpty();
    }

    private StatisticContext context(String indicatorCode, BigDecimal value, String valueText) {
        Country country = Country.builder()
                .id(2L)
                .isoAlpha2("JP")
                .isoAlpha3("JPN")
                .nameEn("Japan")
                .region("Asia")
                .subregion("Eastern Asia")
                .build();

        CountryStatistic statistic = CountryStatistic.builder()
                .countryId(2L)
                .source(DataSource.GEONAMES)
                .indicatorCode(indicatorCode)
                .indicatorName(indicatorCode)
                .category(Category.GEOGRAPHY)
                .value(value)
                .valueText(valueText)
                .year(2026)
                .build();

        return new StatisticContext(
                country,
                statistic,
                120,
                180,
                indicatorCode,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    private List<CountryStatistic> buildLanguageStats(String langCode, int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> CountryStatistic.builder()
                        .countryId((long) (100 + i))
                        .source(DataSource.GEONAMES)
                        .indicatorCode("GEO_PRIMARY_LANGUAGE")
                        .indicatorName("GEO_PRIMARY_LANGUAGE")
                        .category(Category.GEOGRAPHY)
                        .valueText(langCode)
                        .build())
                .toList();
    }

    private CountryStatistic buildStat(Long countryId, String indicator, BigDecimal value, String valueText) {
        return CountryStatistic.builder()
                .countryId(countryId)
                .source(DataSource.GEONAMES)
                .indicatorCode(indicator)
                .indicatorName(indicator)
                .category(Category.GEOGRAPHY)
                .value(value)
                .valueText(valueText)
                .year(2026)
                .build();
    }

    private void registerAllVariants(Map<String, String> templates, String baseCode, String template) {
        for (int i = 1; i <= 3; i++) {
            templates.put(baseCode + i, template);
        }
    }
}
