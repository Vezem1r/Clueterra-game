package io.clueterra.questions.clients.worldbank.hintgeneration;

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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class WorldBankLensTest {

    private StatisticRepository statisticRepository;
    private WorldBankLens lens;

    @BeforeEach
    void setUp() {
        Map<String, String> templates = new HashMap<>();
        registerAllVariants(templates, "WB_POPULATION_GIANT_", "population {population} {ordinal}");
        registerAllVariants(templates, "WB_PROSPERITY_STACK_", "prosperity {gdp_per_capita} {internet_share} {life_expectancy}");
        registerAllVariants(templates, "WB_GDP_HEAVYWEIGHT_", "gdp {value} {ordinal}");
        registerAllVariants(templates, "WB_YOUNG_AGRI_PROFILE_", "young {median_age} {agri_share}");
        registerAllVariants(templates, "WB_URBAN_DOMINANT_", "urban {pct}");
        registerAllVariants(templates, "WB_FERTILITY_HIGH_", "fertility-high {rate}");
        registerAllVariants(templates, "WB_FERTILITY_LOW_", "fertility-low {rate}");
        registerAllVariants(templates, "WB_FOREST_HEAVY_", "forest {pct}");
        registerAllVariants(templates, "WB_ELECTRICITY_GAP_", "electricity {pct}");
        registerAllVariants(templates, "WB_MOBILE_SATURATED_", "mobile {count}");
        registerAllVariants(templates, "WB_CONCRETE_JUNGLE_", "jungle {urban} {density} {internet}");
        registerAllVariants(templates, "WB_DARK_RURAL_", "dark {electricity} {urban} {agri}");
        registerAllVariants(templates, "WB_BABY_BOOM_", "boom {fertility} {growth}");
        registerAllVariants(templates, "WB_POP_GROWTH_BOOM_", "growth-boom {rate}");
        registerAllVariants(templates, "WB_POP_GROWTH_SHRINK_", "growth-shrink {rate}");
        registerAllVariants(templates, "WB_YOUTH_BULGE_", "youth {pct}");
        registerAllVariants(templates, "WB_SILVER_SOCIETY_", "silver {pct}");
        registerAllVariants(templates, "WB_MIGRATION_MAGNET_", "magnet {count}");
        registerAllVariants(templates, "WB_BRAIN_DRAIN_", "drain {count}");
        registerAllVariants(templates, "WB_GDP_BOOM_", "boom {rate}");
        registerAllVariants(templates, "WB_GDP_CRASH_", "crash {rate}");
        registerAllVariants(templates, "WB_HYPERINFLATION_", "hyperinflation {rate}");
        registerAllVariants(templates, "WB_UNEMPLOYMENT_CRISIS_", "unemployment {pct}");
        registerAllVariants(templates, "WB_DEBT_HEAVY_", "debt {pct}");
        registerAllVariants(templates, "WB_HIGH_INEQUALITY_", "inequality {value}");
        registerAllVariants(templates, "WB_VERY_EQUAL_", "equal {value}");
        registerAllVariants(templates, "WB_INDUSTRIAL_POWERHOUSE_", "industry {pct}");
        registerAllVariants(templates, "WB_RESOURCE_DEPENDENT_", "resource {pct}");
        registerAllVariants(templates, "WB_TOURISM_GIANT_", "tourism {value} {ordinal}");
        registerAllVariants(templates, "WB_FINANCIAL_FORTRESS_", "fortress {value} {ordinal}");
        registerAllVariants(templates, "WB_REMITTANCE_GIANT_", "remittance {value} {ordinal}");
        registerAllVariants(templates, "WB_INFANT_MORTALITY_CRISIS_", "infant-crisis {value}");
        registerAllVariants(templates, "WB_INFANT_CARE_EXEMPLARY_", "infant-care {value}");
        registerAllVariants(templates, "WB_DOCTOR_RICH_", "doctor-rich {value}");
        registerAllVariants(templates, "WB_DOCTOR_DESERT_", "doctor-desert {value}");
        registerAllVariants(templates, "WB_HEAVY_DRINKING_", "drinking {value}");
        registerAllVariants(templates, "WB_HEALTHCARE_BIG_SPENDER_", "health-spend {pct}");
        registerAllVariants(templates, "WB_HIV_CRISIS_", "hiv {pct}");
        registerAllVariants(templates, "WB_LITERACY_GAP_", "literacy {pct}");
        registerAllVariants(templates, "WB_EDUCATION_PRIORITY_", "education {pct}");
        registerAllVariants(templates, "WB_UNIVERSITY_NATION_", "university {pct}");
        registerAllVariants(templates, "WB_SMOG_CRISIS_", "smog {value}");
        registerAllVariants(templates, "WB_PRISTINE_AIR_", "pristine {value}");
        registerAllVariants(templates, "WB_WATER_STRESS_", "water {pct}");
        registerAllVariants(templates, "WB_GREEN_GRID_", "grid {pct}");
        registerAllVariants(templates, "WB_BROADBAND_SATURATED_", "broadband {value}");
        registerAllVariants(templates, "WB_VAST_TERRITORY_", "vast {area} {ordinal}");
        registerAllVariants(templates, "WB_TINY_TERRITORY_", "tiny {area}");
        registerAllVariants(templates, "WB_FARMLAND_NATION_", "farmland {pct}");
        IndicatorConfigTestSupport.installTemplates(templates);

        statisticRepository = mock(StatisticRepository.class);
        when(statisticRepository.findBySource(DataSource.WORLD_BANK)).thenReturn(List.of());

        DynamicDifficultyCalculator difficultyCalculator = new DynamicDifficultyCalculator();
        WorldBankAmbiguityCalculator ambiguityCalculator = new WorldBankAmbiguityCalculator();
        lens = new WorldBankLens(statisticRepository, difficultyCalculator, ambiguityCalculator);
    }

    @AfterEach
    void tearDown() {
        IndicatorConfigTestSupport.clear();
    }

    @Test
    void generatesComboWithExplicitEconomyCategory() {
        Country country = Country.builder()
                .id(5L)
                .isoAlpha2("NO")
                .isoAlpha3("NOR")
                .nameEn("Norway")
                .region("Europe")
                .subregion("Northern Europe")
                .build();

        when(statisticRepository.findBySource(DataSource.WORLD_BANK)).thenReturn(List.of(
                stat(country.getId(), "NY.GDP.PCAP.CD", Category.ECONOMY, new BigDecimal("95000")),
                stat(country.getId(), "IT.NET.USER.ZS", Category.INFRASTRUCTURE, new BigDecimal("98")),
                stat(country.getId(), "SP.DYN.LE00.IN", Category.HEALTH, new BigDecimal("83.4")),
                stat(country.getId(), "SP.POP.TOTL", Category.DEMOGRAPHICS, new BigDecimal("5500000"))
        ));

        List<CandidateHint> hints = lens.generate(context(country));

        assertThat(hints)
                .isNotEmpty()
                .anyMatch(hint -> hint.sourceIndicator().startsWith("WB_PROSPERITY_STACK_")
                        && hint.category() == Category.ECONOMY
                        && hint.text().contains("95"));
    }

    @Test
    void generatesGdpRankingHintForTopEconomy() {
        Country country = country("US", "USA", "United States");

        List<CandidateHint> hints = lens.generate(context(
                country,
                "NY.GDP.MKTP.CD",
                Category.ECONOMY,
                new BigDecimal("29000000000000"),
                1
        ));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("WB_GDP_HEAVYWEIGHT_");
            assertThat(hint.text()).contains("1st");
            assertThat(hint.category()).isEqualTo(Category.ECONOMY);
        });
    }

    @Test
    void suppressesWeakPopulationProfileWhenNoThresholdsAreMet() {
        Country country = country("RO", "ROU", "Romania");

        List<CandidateHint> hints = lens.generate(context(
                country,
                "SP.POP.TOTL",
                Category.DEMOGRAPHICS,
                new BigDecimal("19000000"),
                35
        ));

        assertThat(hints).isEmpty();
    }

    @Test
    void generatesYoungAgricultureComboWhenMetricsAlign() {
        Country country = country("UG", "UGA", "Uganda");
        when(statisticRepository.findBySource(DataSource.WORLD_BANK)).thenReturn(List.of(
                stat(country.getId(), "SP.POP.MED", Category.DEMOGRAPHICS, new BigDecimal("18.7")),
                stat(country.getId(), "NV.AGR.TOTL.ZS", Category.ECONOMY, new BigDecimal("23.4")),
                stat(country.getId(), "SP.POP.TOTL", Category.DEMOGRAPHICS, new BigDecimal("50000000"))
        ));

        List<CandidateHint> hints = lens.generate(context(
                country,
                "SP.POP.TOTL",
                Category.DEMOGRAPHICS,
                new BigDecimal("50000000"),
                22
        ));

        assertThat(hints).anySatisfy(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("WB_YOUNG_AGRI_PROFILE_");
            assertThat(hint.text()).contains("18.7");
            assertThat(hint.text()).contains("23.4%");
        });
    }

    @Test
    void generatesUrbanDominantHintWhenHighUrbanization() {
        Country country = country("SG", "SGP", "Singapore");

        List<CandidateHint> hints = lens.generate(context(
                country,
                "SP.URB.TOTL.IN.ZS",
                Category.DEMOGRAPHICS,
                new BigDecimal("100"),
                1
        ));

        assertThat(hints).anySatisfy(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("WB_URBAN_DOMINANT_");
            assertThat(hint.text()).contains("100");
        });
    }

    @Test
    void generatesHighFertilityHint() {
        Country country = country("NE", "NER", "Niger");

        List<CandidateHint> hints = lens.generate(context(
                country,
                "SP.DYN.TFRT.IN",
                Category.DEMOGRAPHICS,
                new BigDecimal("6.8"),
                1
        ));

        assertThat(hints).anySatisfy(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_FERTILITY_HIGH_"));
    }

    @Test
    void generatesForestHeavyHint() {
        Country country = country("SR", "SUR", "Suriname");

        List<CandidateHint> hints = lens.generate(context(
                country,
                "AG.LND.FRST.ZS",
                Category.ENVIRONMENT,
                new BigDecimal("98"),
                1
        ));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_FOREST_HEAVY_"));
    }

    @Test
    void generatesElectricityGapHint() {
        Country country = country("TD", "TCD", "Chad");

        List<CandidateHint> hints = lens.generate(context(
                country,
                "EG.ELC.ACCS.ZS",
                Category.INFRASTRUCTURE,
                new BigDecimal("8"),
                null
        ));

        assertThat(hints).anySatisfy(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_ELECTRICITY_GAP_"));
    }

    @Test
    void generatesPopGrowthBoomHint() {
        Country country = country("NE", "NER", "Niger");

        List<CandidateHint> hints = lens.generate(context(
                country,
                "SP.POP.GROW",
                Category.DEMOGRAPHICS,
                new BigDecimal("3.8"),
                null
        ));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_POP_GROWTH_BOOM_"));
    }

    @Test
    void generatesPopGrowthShrinkHint() {
        Country country = country("LT", "LTU", "Lithuania");

        List<CandidateHint> hints = lens.generate(context(
                country,
                "SP.POP.GROW",
                Category.DEMOGRAPHICS,
                new BigDecimal("-1.2"),
                null
        ));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_POP_GROWTH_SHRINK_"));
    }

    @Test
    void suppressesPopGrowthHintInNormalRange() {
        Country country = country("DE", "DEU", "Germany");

        List<CandidateHint> hints = lens.generate(context(
                country,
                "SP.POP.GROW",
                Category.DEMOGRAPHICS,
                new BigDecimal("0.3"),
                null
        ));

        assertThat(hints).isEmpty();
    }

    @Test
    void generatesYouthBulgeHint() {
        Country country = country("NE", "NER", "Niger");

        List<CandidateHint> hints = lens.generate(context(
                country, "SP.POP.0014.TO.ZS", Category.DEMOGRAPHICS, new BigDecimal("49.5"), null));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_YOUTH_BULGE_"));
    }

    @Test
    void suppressesYouthBulgeWhenBelowThreshold() {
        Country country = country("DE", "DEU", "Germany");

        List<CandidateHint> hints = lens.generate(context(
                country, "SP.POP.0014.TO.ZS", Category.DEMOGRAPHICS, new BigDecimal("13"), null));

        assertThat(hints).isEmpty();
    }

    @Test
    void generatesHyperinflationHint() {
        Country country = country("VE", "VEN", "Venezuela");

        List<CandidateHint> hints = lens.generate(context(
                country, "FP.CPI.TOTL.ZG", Category.ECONOMY, new BigDecimal("50"), null));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_HYPERINFLATION_"));
    }

    @Test
    void generatesGiniHighInequalityHint() {
        Country country = country("ZA", "ZAF", "South Africa");

        List<CandidateHint> hints = lens.generate(context(
                country, "SI.POV.GINI", Category.ECONOMY, new BigDecimal("63"), null));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_HIGH_INEQUALITY_"));
    }

    @Test
    void generatesGiniVeryEqualHint() {
        Country country = country("SK", "SVK", "Slovakia");

        List<CandidateHint> hints = lens.generate(context(
                country, "SI.POV.GINI", Category.ECONOMY, new BigDecimal("25"), null));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_VERY_EQUAL_"));
    }

    @Test
    void suppressesGiniInNormalRange() {
        Country country = country("FR", "FRA", "France");

        List<CandidateHint> hints = lens.generate(context(
                country, "SI.POV.GINI", Category.ECONOMY, new BigDecimal("35"), null));

        assertThat(hints).isEmpty();
    }

    @Test
    void generatesTourismGiantHint() {
        Country country = country("FR", "FRA", "France");

        List<CandidateHint> hints = lens.generate(context(
                country, "ST.INT.RCPT.CD", Category.ECONOMY, new BigDecimal("65000000000"), 3));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("WB_TOURISM_GIANT_");
            assertThat(hint.text()).contains("3rd");
        });
    }

    @Test
    void generatesInfantMortalityCrisisHint() {
        Country country = country("SL", "SLE", "Sierra Leone");

        List<CandidateHint> hints = lens.generate(context(
                country, "SH.DYN.MORT", Category.HEALTH, new BigDecimal("80"), null));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_INFANT_MORTALITY_CRISIS_"));
    }

    @Test
    void generatesSmogCrisisHint() {
        Country country = country("BD", "BGD", "Bangladesh");

        List<CandidateHint> hints = lens.generate(context(
                country, "EN.ATM.PM25.MC.M3", Category.ENVIRONMENT, new BigDecimal("77"), null));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_SMOG_CRISIS_"));
    }

    @Test
    void generatesVastTerritoryHint() {
        Country country = country("RU", "RUS", "Russia");

        List<CandidateHint> hints = lens.generate(context(
                country, "AG.LND.TOTL.K2", Category.ENVIRONMENT, new BigDecimal("17098242"), 1));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("WB_VAST_TERRITORY_");
            assertThat(hint.text()).contains("1st");
        });
    }

    @Test
    void generatesTinyTerritoryHint() {
        Country country = country("MC", "MCO", "Monaco");

        List<CandidateHint> hints = lens.generate(context(
                country, "AG.LND.TOTL.K2", Category.ENVIRONMENT, new BigDecimal("2"), null));

        assertThat(hints).singleElement().satisfies(hint ->
                assertThat(hint.sourceIndicator()).startsWith("WB_TINY_TERRITORY_"));
    }

    private StatisticContext context(Country country) {
        return context(country, "SP.POP.TOTL", Category.DEMOGRAPHICS, new BigDecimal("5500000"), 8);
    }

    private StatisticContext context(Country country,
                                     String indicatorCode,
                                     Category category,
                                     BigDecimal value,
                                     Integer rank) {
        CountryStatistic statistic = CountryStatistic.builder()
                .countryId(country.getId())
                .source(DataSource.WORLD_BANK)
                .indicatorCode(indicatorCode)
                .indicatorName(indicatorCode)
                .category(category)
                .value(value)
                .globalRank(rank)
                .year(2026)
                .build();

        return new StatisticContext(
                country,
                statistic,
                120,
                180,
                "Population",
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    private Country country(String iso2, String iso3, String name) {
        return Country.builder()
                .id(5L)
                .isoAlpha2(iso2)
                .isoAlpha3(iso3)
                .nameEn(name)
                .region("Test region")
                .subregion("Test subregion")
                .build();
    }

    private CountryStatistic stat(Long countryId, String code, Category category, BigDecimal value) {
        return CountryStatistic.builder()
                .countryId(countryId)
                .source(DataSource.WORLD_BANK)
                .indicatorCode(code)
                .indicatorName(code)
                .category(category)
                .value(value)
                .year(2026)
                .build();
    }

    private void registerAllVariants(Map<String, String> templates, String baseCode, String template) {
        for (int i = 1; i <= 3; i++) {
            templates.put(baseCode + i, template);
        }
    }
}
