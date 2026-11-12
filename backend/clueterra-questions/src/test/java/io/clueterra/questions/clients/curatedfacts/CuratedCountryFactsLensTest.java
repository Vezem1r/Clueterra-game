package io.clueterra.questions.clients.curatedfacts;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.challenge.CountryVisibilityProfileService;
import io.clueterra.questions.service.hint.CountryDifficultyAdjuster;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfig;
import io.clueterra.questions.service.hint.IndicatorConfigService;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CuratedCountryFactsLensTest {

    private final StatisticRepository statisticRepository = mock(StatisticRepository.class);
    private final DynamicDifficultyCalculator difficultyCalculator = mock(DynamicDifficultyCalculator.class);
    private final CuratedCountryFactsAmbiguityCalculator ambiguityCalculator =
            new CuratedCountryFactsAmbiguityCalculator();
    private final CountryVisibilityProfileService visibilityProfileService =
            new CountryVisibilityProfileService();

    @BeforeEach
    void setUpIndicatorConfig() {
        visibilityProfileService.loadProfiles();
        IndicatorConfigService configService = mock(IndicatorConfigService.class);
        when(configService.hasTemplate(anyString())).thenReturn(true);
        when(configService.getTemplate(anyString())).thenAnswer(invocation ->
                templateFor(invocation.getArgument(0)));
        when(configService.getBaseDifficulty(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(configService.getQualityScore(anyString(), any(BigDecimal.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        ReflectionTestUtils.invokeMethod(new IndicatorConfig(configService), "register");

        when(difficultyCalculator.computeDifficulty(anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(difficultyCalculator.computeQuality(
                anyString(), anyInt(), any(BigDecimal.class), anyDouble()))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    @Test
    void relatedFactsBecomeOneCultureTrailAndOneIdentityHint() {
        Country country = country(1L, "SG", "Singapore");
        List<CountryStatistic> statistics = List.of(
                statistic(country, CuratedCountryFactIndicator.SIGNATURE_DISH, "Chilli crab, Hainanese chicken rice"),
                statistic(country, CuratedCountryFactIndicator.SIGNATURE_DRINK, "Kopi"),
                statistic(country, CuratedCountryFactIndicator.TRADITIONAL_DANCE, "Lion dance"),
                statistic(country, CuratedCountryFactIndicator.TRADITIONAL_INSTRUMENT, "Dizi"),
                statistic(country, CuratedCountryFactIndicator.STATEHOOD_DATE, "9 August 1965"),
                statistic(country, CuratedCountryFactIndicator.ASSOCIATED_MOTTO, "Onward"));
        when(statisticRepository.findBySource(DataSource.CURATED_COUNTRY_FACTS)).thenReturn(statistics);

        CuratedCountryFactsLens lens = lens();

        assertOnlyHintStartsWith(lens, country, statistics.get(0), "CURATED_CULTURE_TRAIL_");
        assertThat(lens.generate(context(country, statistics.get(0))))
                .singleElement()
                .satisfies(hint -> assertThat(hint.text())
                        .contains("try Chilli crab")
                        .contains("sip Kopi")
                        .contains("watch dancers perform Lion dance")
                        .doesNotContain("Hainanese chicken rice", "Dizi"));
        assertThat(lens.generate(context(country, statistics.get(1)))).isEmpty();
        assertThat(lens.generate(context(country, statistics.get(2)))).isEmpty();
        assertThat(lens.generate(context(country, statistics.get(3)))).isEmpty();
        assertOnlyHintStartsWith(lens, country, statistics.get(4), "CURATED_IDENTITY_PAIR_");
        assertThat(lens.generate(context(country, statistics.get(5)))).isEmpty();
    }

    @Test
    void zimbabweFactsBecomeThreeRichHintsWithoutRepeatedComponents() {
        Country country = country(5L, "ZW", "Zimbabwe");
        List<CountryStatistic> statistics = List.of(
                statistic(country, CuratedCountryFactIndicator.SIGNATURE_DISH, "Sadza"),
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_ANIMAL, "Bateleur"),
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_BIRD, "African fish eagle"),
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_FLOWER, "Flame lily"),
                statistic(country, CuratedCountryFactIndicator.TRADITIONAL_INSTRUMENT, "Mbira"),
                statistic(country, CuratedCountryFactIndicator.STATEHOOD_DATE, "18 April 1980"),
                statistic(country, CuratedCountryFactIndicator.ASSOCIATED_MOTTO, "Unity, Freedom, Work"));
        when(statisticRepository.findBySource(DataSource.CURATED_COUNTRY_FACTS)).thenReturn(statistics);

        CuratedCountryFactsLens lens = lens();

        assertThat(lens.generate(context(country, statistics.get(0))))
                .singleElement()
                .satisfies(hint -> {
                    assertThat(hint.text())
                            .contains("try Sadza")
                            .contains("listen for musicians playing the Mbira");
                    assertThat(hint.estimatedDifficulty()).isEqualTo(45);
                });
        assertThat(lens.generate(context(country, statistics.get(1))))
                .singleElement()
                .satisfies(hint -> {
                    assertThat(hint.text())
                            .contains("the Bateleur", "the African fish eagle", "the Flame lily");
                    assertThat(hint.estimatedDifficulty()).isEqualTo(47);
                });
        assertThat(lens.generate(context(country, statistics.get(5))))
                .singleElement()
                .satisfies(hint -> {
                    assertThat(hint.text()).contains("18 April 1980", "Unity, Freedom, Work");
                    assertThat(hint.estimatedDifficulty()).isEqualTo(48);
                });

        assertThat(statistics.subList(2, 5))
                .allSatisfy(statistic -> assertThat(lens.generate(context(country, statistic))).isEmpty());
        assertThat(lens.generate(context(country, statistics.get(6)))).isEmpty();
    }

    @Test
    void countryVisibilityTierRaisesOtherwiseIdenticalClueDifficulty() {
        Country unitedStates = country(10L, "US", "United States");
        Country unitedArabEmirates = country(11L, "AE", "United Arab Emirates");
        Country zimbabwe = country(12L, "ZW", "Zimbabwe");
        Country saintVincent = country(13L, "VC", "Saint Vincent and the Grenadines");
        List<CountryStatistic> statistics = List.of(
                statistic(unitedStates, CuratedCountryFactIndicator.SIGNATURE_DISH, "Apple pie"),
                statistic(unitedArabEmirates, CuratedCountryFactIndicator.SIGNATURE_DISH, "Harees"),
                statistic(zimbabwe, CuratedCountryFactIndicator.SIGNATURE_DISH, "Sadza"),
                statistic(saintVincent, CuratedCountryFactIndicator.SIGNATURE_DISH, "Roasted breadfruit"));
        when(statisticRepository.findBySource(DataSource.CURATED_COUNTRY_FACTS)).thenReturn(statistics);

        CuratedCountryFactsLens lens = lens();

        assertThat(lens.generate(context(unitedStates, statistics.get(0))))
                .singleElement()
                .extracting(CandidateHint::estimatedDifficulty)
                .isEqualTo(24);
        assertThat(lens.generate(context(unitedArabEmirates, statistics.get(1))))
                .singleElement()
                .extracting(CandidateHint::estimatedDifficulty)
                .isEqualTo(34);
        assertThat(lens.generate(context(zimbabwe, statistics.get(2))))
                .singleElement()
                .extracting(CandidateHint::estimatedDifficulty)
                .isEqualTo(46);
        assertThat(lens.generate(context(saintVincent, statistics.get(3))))
                .singleElement()
                .extracting(CandidateHint::estimatedDifficulty)
                .isEqualTo(60);
    }

    @Test
    void symbolTrailSkipsDuplicateAnimalAndBird() {
        Country country = country(2L, "BR", "Brazil");
        List<CountryStatistic> statistics = List.of(
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_ANIMAL, "Rufous-bellied thrush"),
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_BIRD, "Rufous-bellied thrush"),
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_FLOWER, "Ipê-amarelo"));
        when(statisticRepository.findBySource(DataSource.CURATED_COUNTRY_FACTS)).thenReturn(statistics);

        CuratedCountryFactsLens lens = lens();
        List<CandidateHint> animalHints = lens.generate(context(country, statistics.get(0)));

        assertThat(animalHints)
                .singleElement()
                .satisfies(hint -> {
                    assertThat(hint.sourceIndicator()).startsWith("CURATED_SYMBOL_TRAIL_");
                    assertThat(hint.text()).contains("Rufous-bellied thrush", "Ipê-amarelo");
                });
        assertThat(lens.generate(context(country, statistics.get(1)))).isEmpty();
        assertThat(lens.generate(context(country, statistics.get(2)))).isEmpty();
    }

    @Test
    void duplicateSymbolsWithoutAnotherCompanionProduceOnlyOneHint() {
        Country country = country(6L, "SV", "El Salvador");
        List<CountryStatistic> statistics = List.of(
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_ANIMAL, "Turquoise-browed motmot"),
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_BIRD, "Turquoise-browed motmot"));
        when(statisticRepository.findBySource(DataSource.CURATED_COUNTRY_FACTS)).thenReturn(statistics);

        CuratedCountryFactsLens lens = lens();

        assertThat(lens.generate(context(country, statistics.get(0)))).hasSize(1);
        assertThat(lens.generate(context(country, statistics.get(1)))).isEmpty();
    }

    @Test
    void countryNamesInsideListFactsAreRemovedBeforeTrailTemplating() {
        Country country = country(3L, "CU", "Cuba");
        List<CountryStatistic> statistics = List.of(
                statistic(country, CuratedCountryFactIndicator.SIGNATURE_DISH, "Ropa vieja"),
                statistic(country, CuratedCountryFactIndicator.SIGNATURE_DRINK,
                        "Mojito, Cuba Libre, Daiquiri"));
        when(statisticRepository.findBySource(DataSource.CURATED_COUNTRY_FACTS)).thenReturn(statistics);

        List<CandidateHint> hints = lens().generate(context(country, statistics.getFirst()));

        assertThat(hints)
                .singleElement()
                .satisfies(hint -> assertThat(hint.text())
                        .contains("Ropa vieja", "Mojito")
                        .doesNotContain("Cuba Libre", "Daiquiri", "this country Libre"));
    }

    @Test
    void answerRevealingSymbolsAreSkippedWithoutSuppressingSafeSymbol() {
        Country country = country(4L, "VC", "Saint Vincent and the Grenadines");
        List<CountryStatistic> statistics = List.of(
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_ANIMAL,
                        "Saint Vincent amazon parrot"),
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_BIRD,
                        "St Vincent parrot"),
                statistic(country, CuratedCountryFactIndicator.EMBLEMATIC_TREE,
                        "Soufrière Tree"));
        when(statisticRepository.findBySource(DataSource.CURATED_COUNTRY_FACTS)).thenReturn(statistics);

        CuratedCountryFactsLens lens = lens();

        assertThat(lens.generate(context(country, statistics.get(0)))).isEmpty();
        assertThat(lens.generate(context(country, statistics.get(1)))).isEmpty();
        assertThat(lens.generate(context(country, statistics.get(2))))
                .singleElement()
                .satisfies(hint -> assertThat(hint.text()).contains("Soufrière Tree"));
    }

    private void assertOnlyHintStartsWith(CuratedCountryFactsLens lens,
                                          Country country,
                                          CountryStatistic statistic,
                                          String indicatorPrefix) {
        assertThat(lens.generate(context(country, statistic)))
                .singleElement()
                .extracting(CandidateHint::sourceIndicator)
                .asString()
                .startsWith(indicatorPrefix);
    }

    private String templateFor(String code) {
        if (code.startsWith("CURATED_CULTURE_TRAIL_")) {
            return "One country links these clues: {clues}.";
        }
        if (code.startsWith("CURATED_SYMBOL_TRAIL_")) {
            return "These symbols all point to one country: {symbols}.";
        }
        if (code.startsWith("CURATED_IDENTITY_PAIR_")) {
            return "Mark {date} on the calendar; the motto \"{motto}\" gives you another clue.";
        }
        return Stream.of(CuratedCountryFactIndicator.values())
                .filter(indicator -> code.startsWith(indicator.getCode() + "_"))
                .map(indicator -> "{" + indicator.getPlaceholder() + "}")
                .findFirst()
                .orElse("");
    }

    private CuratedCountryFactsLens lens() {
        return new CuratedCountryFactsLens(
                statisticRepository,
                difficultyCalculator,
                ambiguityCalculator,
                new CountryDifficultyAdjuster(visibilityProfileService));
    }

    private StatisticContext context(Country country, CountryStatistic statistic) {
        return StatisticContext.builder()
                .country(country)
                .statistic(statistic)
                .build();
    }

    private Country country(Long id, String code, String name) {
        return Country.builder()
                .id(id)
                .isoAlpha2(code)
                .nameEn(name)
                .build();
    }

    private CountryStatistic statistic(Country country,
                                       CuratedCountryFactIndicator indicator,
                                       String value) {
        return CountryStatistic.builder()
                .id((long) (indicator.ordinal() + 1))
                .countryId(country.getId())
                .source(DataSource.CURATED_COUNTRY_FACTS)
                .indicatorCode(indicator.getCode())
                .valueText(value)
                .confidence(BigDecimal.valueOf(0.82))
                .build();
    }
}
