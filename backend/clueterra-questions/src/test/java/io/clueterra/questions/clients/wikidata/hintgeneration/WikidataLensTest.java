package io.clueterra.questions.clients.wikidata.hintgeneration;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.challenge.CountryVisibilityProfileService;
import io.clueterra.questions.service.hint.CountryDifficultyAdjuster;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfigTestSupport;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WikidataLensTest {

    private WikidataLens lens;

    @BeforeEach
    void setUp() {
        Map<String, String> templates = new HashMap<>();
        registerVariants(templates, "WIKIDATA_NATIONAL_ANTHEM_", "anthem {anthem}");
        registerVariants(templates, "WIKIDATA_CALLING_CODE_", "calling {calling_code}");
        registerVariants(templates, "WIKIDATA_TOP_LEVEL_DOMAIN_", "domain {domain}");
        registerVariants(templates, "WIKIDATA_ELECTRICAL_PLUG_", "plug {plug}");
        registerVariants(templates, "WIKIDATA_CONTACT_PAIR_", "contact {calling_code} {domain}");
        registerVariants(templates, "WIKIDATA_EVERYDAY_INFRASTRUCTURE_", "travel {driving_side} {plug}");
        IndicatorConfigTestSupport.installTemplates(templates);

        List<CountryStatistic> statistics = new ArrayList<>(List.of(
                statistic(4L, "WIKIDATA_CALLING_CODE", "+1"),
                statistic(4L, "WIKIDATA_TOP_LEVEL_DOMAIN", ".us"),
                statistic(4L, "WIKIDATA_DRIVING_SIDE", "right"),
                statistic(4L, "WIKIDATA_ELECTRICAL_PLUG", "NEMA 1-15, NEMA 5-15"),
                statistic(4L, "WIKIDATA_NATIONAL_ANTHEM", "The Star-Spangled Banner"),
                statistic(8L, "WIKIDATA_NATIONAL_ANTHEM", "The Star-Spangled Banner"),
                statistic(12L, "WIKIDATA_CALLING_CODE", "+263"),
                statistic(12L, "WIKIDATA_TOP_LEVEL_DOMAIN", ".zw"),
                statistic(12L, "WIKIDATA_DRIVING_SIDE", "left"),
                statistic(12L, "WIKIDATA_ELECTRICAL_PLUG",
                        "AC power plugs and sockets: British and related types, BS 1363"),
                statistic(12L, "WIKIDATA_NATIONAL_ANTHEM", "National Anthem of Zimbabwe"),
                statistic(20L, "WIKIDATA_CALLING_CODE", "+999"),
                statistic(21L, "WIKIDATA_TOP_LEVEL_DOMAIN", ".xy"),
                statistic(22L, "WIKIDATA_ELECTRICAL_PLUG", "Schuko")
        ));
        for (long countryId = 30L; countryId <= 35L; countryId++) {
            statistics.add(statistic(countryId, "WIKIDATA_NATIONAL_ANTHEM", "God Save the King"));
        }
        statistics.add(statistic(40L, "WIKIDATA_CALLING_CODE", "+777"));
        for (long countryId = 41L; countryId <= 51L; countryId++) {
            statistics.add(statistic(countryId, "WIKIDATA_CALLING_CODE", "+777"));
        }
        statistics.add(statistic(60L, "WIKIDATA_CALLING_CODE", "+666"));
        for (long countryId = 61L; countryId <= 70L; countryId++) {
            statistics.add(statistic(countryId, "WIKIDATA_CALLING_CODE", "+666"));
        }

        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findBySource(DataSource.WIKIDATA)).thenReturn(statistics);
        CountryVisibilityProfileService visibilityProfileService = new CountryVisibilityProfileService();
        visibilityProfileService.loadProfiles();
        lens = new WikidataLens(
                repository,
                new DynamicDifficultyCalculator(),
                new WikidataAmbiguityCalculator(),
                new CountryDifficultyAdjuster(visibilityProfileService));
    }

    @AfterEach
    void tearDown() {
        IndicatorConfigTestSupport.clear();
    }

    @Test
    void contactPairReplacesSeparateCallingCodeAndDomainHints() {
        List<CandidateHint> hints = lens.generate(context(
                4L, "US", "United States", "WIKIDATA_CALLING_CODE", "+1"));

        assertThat(hints)
                .singleElement()
                .satisfies(hint -> {
                    assertThat(hint.sourceIndicator()).isEqualTo("WIKIDATA_CONTACT_PAIR");
                    assertThat(hint.text()).contains("+1", ".us");
                });
        assertThat(lens.generate(context(
                4L, "US", "United States", "WIKIDATA_TOP_LEVEL_DOMAIN", ".us")))
                .isEmpty();
    }

    @Test
    void everydayPairReplacesPlugHintAndUsesFriendlyPlugNames() {
        List<CandidateHint> hints = lens.generate(context(
                4L, "US", "United States", "WIKIDATA_DRIVING_SIDE", "right"));

        assertThat(hints)
                .singleElement()
                .satisfies(hint -> {
                    assertThat(hint.sourceIndicator()).isEqualTo("WIKIDATA_EVERYDAY_INFRASTRUCTURE");
                    assertThat(hint.text())
                            .contains("right", "Type A", "Type B")
                            .doesNotContain("NEMA");
                });
        assertThat(lens.generate(context(
                4L, "US", "United States", "WIKIDATA_ELECTRICAL_PLUG", "NEMA 1-15, NEMA 5-15")))
                .isEmpty();
    }

    @Test
    void answerRevealingAnthemIsSkipped() {
        assertThat(lens.generate(context(
                12L, "ZW", "Zimbabwe", "WIKIDATA_NATIONAL_ANTHEM", "National Anthem of Zimbabwe")))
                .isEmpty();
    }

    @Test
    void overlySharedAnthemIsSkipped() {
        assertThat(lens.generate(context(
                30L, "GB", "United Kingdom", "WIKIDATA_NATIONAL_ANTHEM", "God Save the King")))
                .isEmpty();
    }

    @Test
    void everyHintAboveGlobalAmbiguityLimitIsSkipped() {
        assertThat(lens.generate(context(
                40L, "XX", "Testland", "WIKIDATA_CALLING_CODE", "+777")))
                .isEmpty();
        assertThat(lens.generate(context(
                60L, "XX", "Testland", "WIKIDATA_CALLING_CODE", "+666")))
                .singleElement()
                .extracting(CandidateHint::ambiguityCount)
                .isEqualTo(10);
    }

    @Test
    void exactAnthemAmbiguityRaisesDifficultyAndReducesQuality() {
        CandidateHint hint = lens.generate(context(
                4L, "US", "United States", "WIKIDATA_NATIONAL_ANTHEM", "The Star-Spangled Banner"))
                .getFirst();

        assertThat(hint.ambiguityCount()).isEqualTo(1);
        assertThat(hint.estimatedDifficulty()).isEqualTo(35);
        assertThat(hint.estimatedQuality()).isLessThan(new BigDecimal("0.90"));
    }

    @Test
    void hardCountryRaisesOtherwiseEasyContactClueDifficulty() {
        CandidateHint unitedStates = lens.generate(context(
                4L, "US", "United States", "WIKIDATA_CALLING_CODE", "+1")).getFirst();
        CandidateHint zimbabwe = lens.generate(context(
                12L, "ZW", "Zimbabwe", "WIKIDATA_CALLING_CODE", "+263")).getFirst();

        assertThat(unitedStates.estimatedDifficulty()).isEqualTo(21);
        assertThat(zimbabwe.estimatedDifficulty()).isEqualTo(45);
    }

    @Test
    void singleFactsRemainAsFallbacksWhenTheirPartnerIsMissing() {
        assertThat(lens.generate(context(
                20L, "US", "Testland", "WIKIDATA_CALLING_CODE", "+999")))
                .singleElement()
                .extracting(CandidateHint::sourceIndicator)
                .isEqualTo("WIKIDATA_CALLING_CODE");
        assertThat(lens.generate(context(
                21L, "US", "Testland", "WIKIDATA_TOP_LEVEL_DOMAIN", ".xy")))
                .singleElement()
                .extracting(CandidateHint::sourceIndicator)
                .isEqualTo("WIKIDATA_TOP_LEVEL_DOMAIN");
        assertThat(lens.generate(context(
                22L, "US", "Testland", "WIKIDATA_ELECTRICAL_PLUG", "Schuko")))
                .singleElement()
                .satisfies(hint -> {
                    assertThat(hint.sourceIndicator()).isEqualTo("WIKIDATA_ELECTRICAL_PLUG");
                    assertThat(hint.text()).contains("Type F").doesNotContain("Schuko");
                });
    }

    @Test
    void ignoresIndicatorsOutsideTheNarrowWikidataContract() {
        StatisticContext context = context(
                4L, "US", "United States", "WIKIDATA_NATIONAL_PARK_COUNT", "63");

        assertThat(lens.canApply(context)).isFalse();
        assertThat(lens.generate(context)).isEmpty();
    }

    private StatisticContext context(Long countryId,
                                     String countryCode,
                                     String countryName,
                                     String indicatorCode,
                                     String valueText) {
        Country country = Country.builder()
                .id(countryId)
                .isoAlpha2(countryCode)
                .nameEn(countryName)
                .build();
        CountryStatistic statistic = statistic(countryId, indicatorCode, valueText);
        statistic.setConfidence(new BigDecimal("0.95"));
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
                Optional.empty());
    }

    private CountryStatistic statistic(Long countryId, String indicatorCode, String valueText) {
        return CountryStatistic.builder()
                .countryId(countryId)
                .source(DataSource.WIKIDATA)
                .indicatorCode(indicatorCode)
                .indicatorName(indicatorCode)
                .category(Category.INFRASTRUCTURE)
                .valueText(valueText)
                .confidence(BigDecimal.ONE)
                .weight(BigDecimal.ONE)
                .build();
    }

    private void registerVariants(Map<String, String> templates, String prefix, String template) {
        for (int index = 1; index <= 3; index++) {
            templates.put(prefix + index, template);
        }
    }
}
