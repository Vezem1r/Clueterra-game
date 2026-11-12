package io.clueterra.questions.service.hint.combinatorics;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.CombinatoricsConfigRepository;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.HintLifecycleService;
import io.clueterra.questions.service.hint.SourceOwnedLifecycleResult;
import io.clueterra.questions.service.hint.IndicatorTemplateRenderer;
import io.clueterra.questions.service.hint.combinatorics.dto.*;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CombinatoricsHintEngineTest {

    @Test
    void generateForCountry_buildsComboHintWithStableVariantAndAmbiguity() {
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        CombinatoricsConfigRepository configRepository = mock(CombinatoricsConfigRepository.class);
        HintLifecycleService hintLifecycleService = mock(HintLifecycleService.class);
        IndicatorTemplateRenderer templateRenderer = new IndicatorTemplateRenderer();
        CombinatoricsHintDifficultyCalculator difficultyCalculator = new CombinatoricsHintDifficultyCalculator();
        CombinatoricsAmbiguityCalculator ambiguityCalculator = new CombinatoricsAmbiguityCalculator();

        CombinatoricsHintEngine engine = new CombinatoricsHintEngine(
                statisticRepository,
                configRepository,
                hintLifecycleService,
                templateRenderer,
                difficultyCalculator,
                ambiguityCalculator
        );

        ComboHintDefinition definition = new ComboHintDefinition(
                "COMBO_LONGEVITY_LOW_FERTILITY",
                "longevity with low fertility",
                Category.DEMOGRAPHICS,
                HintType.UNIQUE_FEATURE,
                "PROFILE",
                66,
                new BigDecimal("0.86"),
                80,
                List.of(
                        new ComboHintComponent(
                                "COMBO_LONGEVITY_LOW_FERTILITY",
                                "life",
                                DataSource.OUR_WORLD_IN_DATA,
                                "OWID_LIFE_EXPECTANCY",
                                ComboValueFormatter.DECIMAL_1,
                                true
                        ),
                        new ComboHintComponent(
                                "COMBO_LONGEVITY_LOW_FERTILITY",
                                "fertility",
                                DataSource.OUR_WORLD_IN_DATA,
                                "OWID_FERTILITY_RATE",
                                ComboValueFormatter.DECIMAL_1,
                                true
                        )
                ),
                List.of(
                        new ComboHintPredicate(
                                "COMBO_LONGEVITY_LOW_FERTILITY",
                                1,
                                "life",
                                ComboPredicateOperator.GTE,
                                ComboPredicateMode.CONSTANT,
                                new BigDecimal("80.0"),
                                null
                        ),
                        new ComboHintPredicate(
                                "COMBO_LONGEVITY_LOW_FERTILITY",
                                2,
                                "fertility",
                                ComboPredicateOperator.LTE,
                                ComboPredicateMode.CONSTANT,
                                new BigDecimal("2.1"),
                                null
                        )
                ),
                List.of(
                        new ComboTextVariant("COMBO_LONGEVITY_LOW_FERTILITY", 1, "A: life {life}, fertility {fertility}."),
                        new ComboTextVariant("COMBO_LONGEVITY_LOW_FERTILITY", 2, "B: life {life}, fertility {fertility}."),
                        new ComboTextVariant("COMBO_LONGEVITY_LOW_FERTILITY", 3, "C: life {life}, fertility {fertility}.")
                )
        );

        Country country = Country.builder()
                .id(10L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();

        when(configRepository.findActiveDefinitions()).thenReturn(List.of(definition));
        when(statisticRepository.findAll()).thenReturn(List.of(
                stat(10L, DataSource.OUR_WORLD_IN_DATA, "OWID_LIFE_EXPECTANCY", "82.2"),
                stat(10L, DataSource.OUR_WORLD_IN_DATA, "OWID_FERTILITY_RATE", "1.7"),
                stat(20L, DataSource.OUR_WORLD_IN_DATA, "OWID_LIFE_EXPECTANCY", "81.0"),
                stat(20L, DataSource.OUR_WORLD_IN_DATA, "OWID_FERTILITY_RATE", "2.0"),
                stat(30L, DataSource.OUR_WORLD_IN_DATA, "OWID_LIFE_EXPECTANCY", "74.0"),
                stat(30L, DataSource.OUR_WORLD_IN_DATA, "OWID_FERTILITY_RATE", "4.1")
        ));
        when(hintLifecycleService.applySourceOwnedLifecycle(eq(country), anyList(), eq(Set.of(DataSource.COMBINATORICS))))
                .thenReturn(new SourceOwnedLifecycleResult(List.of(), 1, 0));

        int generatedFirst = engine.generateForCountry(country);
        engine.invalidateCache();
        int generatedSecond = engine.generateForCountry(country);

        assertThat(generatedFirst).isEqualTo(1);
        assertThat(generatedSecond).isEqualTo(1);

        ArgumentCaptor<List<CandidateHint>> captor = ArgumentCaptor.forClass(List.class);
        verify(hintLifecycleService, times(2)).applySourceOwnedLifecycle(
                eq(country),
                captor.capture(),
                eq(Set.of(DataSource.COMBINATORICS))
        );
        verify(configRepository, times(2)).findActiveDefinitions();
        verify(statisticRepository, times(2)).findAll();

        List<List<CandidateHint>> capturedLists = captor.getAllValues();
        assertThat(capturedLists).hasSize(2);
        CandidateHint firstCandidate = capturedLists.get(0).getFirst();
        CandidateHint secondCandidate = capturedLists.get(1).getFirst();

        assertThat(firstCandidate.source()).isEqualTo(DataSource.COMBINATORICS);
        assertThat(firstCandidate.sourceIndicator()).isEqualTo("COMBO_LONGEVITY_LOW_FERTILITY");
        assertThat(firstCandidate.category()).isEqualTo(Category.DEMOGRAPHICS);
        assertThat(firstCandidate.type()).isEqualTo(HintType.UNIQUE_FEATURE);
        assertThat(firstCandidate.ambiguityCount()).isEqualTo(1);
        assertThat(firstCandidate.estimatedDifficulty()).isLessThan(66);
        assertThat(firstCandidate.estimatedDifficulty()).isGreaterThanOrEqualTo(50);
        assertThat(firstCandidate.estimatedQuality()).isGreaterThan(new BigDecimal("0.70"));
        assertThat(firstCandidate.text()).isEqualTo(secondCandidate.text());
        assertThat(firstCandidate.text()).contains("82.2").contains("1.7");
    }

    @Test
    void generateForCountry_cleansUpWhenCountryNoLongerMatchesAnyCombo() {
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        CombinatoricsConfigRepository configRepository = mock(CombinatoricsConfigRepository.class);
        HintLifecycleService hintLifecycleService = mock(HintLifecycleService.class);
        IndicatorTemplateRenderer templateRenderer = new IndicatorTemplateRenderer();
        CombinatoricsHintDifficultyCalculator difficultyCalculator = new CombinatoricsHintDifficultyCalculator();
        CombinatoricsAmbiguityCalculator ambiguityCalculator = new CombinatoricsAmbiguityCalculator();

        CombinatoricsHintEngine engine = new CombinatoricsHintEngine(
                statisticRepository,
                configRepository,
                hintLifecycleService,
                templateRenderer,
                difficultyCalculator,
                ambiguityCalculator
        );

        ComboHintDefinition definition = new ComboHintDefinition(
                "COMBO_HIGH_CARBON_LOW_RENEWABLES",
                "high carbon low renewables",
                Category.ENVIRONMENT,
                HintType.UNIQUE_FEATURE,
                "CONTRAST",
                74,
                new BigDecimal("0.84"),
                30,
                List.of(
                        new ComboHintComponent(
                                "COMBO_HIGH_CARBON_LOW_RENEWABLES",
                                "co2",
                                DataSource.OUR_WORLD_IN_DATA,
                                "OWID_CO2_PER_CAPITA",
                                ComboValueFormatter.DECIMAL_1,
                                true
                        ),
                        new ComboHintComponent(
                                "COMBO_HIGH_CARBON_LOW_RENEWABLES",
                                "renewables",
                                DataSource.OUR_WORLD_IN_DATA,
                                "OWID_RENEWABLES_PCT",
                                ComboValueFormatter.PERCENT_1,
                                true
                        )
                ),
                List.of(
                        new ComboHintPredicate(
                                "COMBO_HIGH_CARBON_LOW_RENEWABLES",
                                1,
                                "co2",
                                ComboPredicateOperator.GTE,
                                ComboPredicateMode.CONSTANT,
                                new BigDecimal("10.0"),
                                null
                        ),
                        new ComboHintPredicate(
                                "COMBO_HIGH_CARBON_LOW_RENEWABLES",
                                2,
                                "renewables",
                                ComboPredicateOperator.LTE,
                                ComboPredicateMode.CONSTANT,
                                new BigDecimal("20.0"),
                                null
                        )
                ),
                List.of(new ComboTextVariant(
                        "COMBO_HIGH_CARBON_LOW_RENEWABLES",
                        1,
                        "CO2 {co2}, renewables {renewables}."
                ))
        );

        Country country = Country.builder()
                .id(10L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();

        when(configRepository.findActiveDefinitions()).thenReturn(List.of(definition));
        when(statisticRepository.findAll()).thenReturn(List.of(
                stat(10L, DataSource.OUR_WORLD_IN_DATA, "OWID_CO2_PER_CAPITA", "7.0"),
                stat(10L, DataSource.OUR_WORLD_IN_DATA, "OWID_RENEWABLES_PCT", "35.0")
        ));
        when(hintLifecycleService.applySourceOwnedLifecycle(eq(country), anyList(), eq(Set.of(DataSource.COMBINATORICS))))
                .thenReturn(SourceOwnedLifecycleResult.empty());

        int generated = engine.generateForCountry(country);

        assertThat(generated).isZero();

        ArgumentCaptor<List<CandidateHint>> captor = ArgumentCaptor.forClass(List.class);
        verify(hintLifecycleService).applySourceOwnedLifecycle(
                eq(country),
                captor.capture(),
                eq(Set.of(DataSource.COMBINATORICS))
        );
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void generateForCountry_groupsAmbiguityByRenderedComboSignature() {
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        CombinatoricsConfigRepository configRepository = mock(CombinatoricsConfigRepository.class);
        HintLifecycleService hintLifecycleService = mock(HintLifecycleService.class);
        IndicatorTemplateRenderer templateRenderer = new IndicatorTemplateRenderer();
        CombinatoricsHintDifficultyCalculator difficultyCalculator = new CombinatoricsHintDifficultyCalculator();
        CombinatoricsAmbiguityCalculator ambiguityCalculator = new CombinatoricsAmbiguityCalculator();

        CombinatoricsHintEngine engine = new CombinatoricsHintEngine(
                statisticRepository,
                configRepository,
                hintLifecycleService,
                templateRenderer,
                difficultyCalculator,
                ambiguityCalculator
        );

        ComboHintDefinition definition = new ComboHintDefinition(
                "COMBO_HIGH_CO2_LOW_SOLAR",
                "high-CO2 low-solar contrast",
                Category.ENVIRONMENT,
                HintType.UNIQUE_FEATURE,
                "CONTRAST",
                63,
                new BigDecimal("0.85"),
                70,
                List.of(
                        new ComboHintComponent(
                                "COMBO_HIGH_CO2_LOW_SOLAR",
                                "co2",
                                DataSource.OUR_WORLD_IN_DATA,
                                "OWID_CO2_PER_CAPITA",
                                ComboValueFormatter.DECIMAL_1,
                                true
                        ),
                        new ComboHintComponent(
                                "COMBO_HIGH_CO2_LOW_SOLAR",
                                "solar",
                                DataSource.CIA_FACTBOOK,
                                "ELECTRICITY_SOLAR_PCT",
                                ComboValueFormatter.PERCENT_1,
                                true
                        )
                ),
                List.of(
                        new ComboHintPredicate(
                                "COMBO_HIGH_CO2_LOW_SOLAR",
                                1,
                                "co2",
                                ComboPredicateOperator.GTE,
                                ComboPredicateMode.CONSTANT,
                                new BigDecimal("9.5"),
                                null
                        ),
                        new ComboHintPredicate(
                                "COMBO_HIGH_CO2_LOW_SOLAR",
                                2,
                                "solar",
                                ComboPredicateOperator.LTE,
                                ComboPredicateMode.CONSTANT,
                                new BigDecimal("3.0"),
                                null
                        )
                ),
                List.of(new ComboTextVariant(
                        "COMBO_HIGH_CO2_LOW_SOLAR",
                        1,
                        "Despite {co2} tonnes of CO2 per person, solar provides only {solar} of electricity here."
                ))
        );

        Country zimbabwe = Country.builder()
                .id(10L)
                .isoAlpha2("ZW")
                .isoAlpha3("ZWE")
                .nameEn("Zimbabwe")
                .build();

        when(configRepository.findActiveDefinitions()).thenReturn(List.of(definition));
        when(statisticRepository.findAll()).thenReturn(List.of(
                stat(10L, DataSource.OUR_WORLD_IN_DATA, "OWID_CO2_PER_CAPITA", "9.64"),
                stat(10L, DataSource.CIA_FACTBOOK, "ELECTRICITY_SOLAR_PCT", "0.44"),
                stat(20L, DataSource.OUR_WORLD_IN_DATA, "OWID_CO2_PER_CAPITA", "9.61"),
                stat(20L, DataSource.CIA_FACTBOOK, "ELECTRICITY_SOLAR_PCT", "0.41"),
                stat(30L, DataSource.OUR_WORLD_IN_DATA, "OWID_CO2_PER_CAPITA", "10.20"),
                stat(30L, DataSource.CIA_FACTBOOK, "ELECTRICITY_SOLAR_PCT", "0.80")
        ));
        when(hintLifecycleService.applySourceOwnedLifecycle(eq(zimbabwe), anyList(), eq(Set.of(DataSource.COMBINATORICS))))
                .thenReturn(new SourceOwnedLifecycleResult(List.of(), 1, 0));

        int generated = engine.generateForCountry(zimbabwe);

        assertThat(generated).isEqualTo(1);

        ArgumentCaptor<List<CandidateHint>> captor = ArgumentCaptor.forClass(List.class);
        verify(hintLifecycleService).applySourceOwnedLifecycle(
                eq(zimbabwe),
                captor.capture(),
                eq(Set.of(DataSource.COMBINATORICS))
        );

        CandidateHint candidate = captor.getValue().getFirst();
        assertThat(candidate.ambiguityCount()).isEqualTo(2);
        assertThat(candidate.text()).contains("9.6").contains("0.4%");
    }

    private CountryStatistic stat(Long countryId, DataSource source, String indicatorCode, String value) {
        return CountryStatistic.builder()
                .countryId(countryId)
                .source(source)
                .indicatorCode(indicatorCode)
                .value(new BigDecimal(value))
                .isCurrent(true)
                .build();
    }
}
