package io.clueterra.questions.clients.ciafactbook.hintgeneration;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CIAFactbookLensTest {

    private CIAFactbookAmbiguityCalculator ambiguityCalculator;
    private CIAFactbookLens lens;

    @BeforeEach
    void setUp() {
        Map<String, String> templates = new HashMap<>();
        registerAllVariants(templates, "CIA_BORDER_COUNTRIES_", "neighbors {neighbors}");
        registerAllVariants(templates, "CIA_CAPITAL_CITY_", "capital {capital}");
        registerAllVariants(templates, "CIA_CHIEF_OF_STATE_", "leader {leader}");
        registerAllVariants(templates, "CIA_CITIZENSHIP_BIRTH_YES_", "birth yes");
        registerAllVariants(templates, "CIA_CURRENCY_", "currency {currency}");
        registerAllVariants(templates, "CIA_INDEPENDENCE_", "independence {independence}");
        registerAllVariants(templates, "CIA_LEGAL_SYSTEM_", "system {system}");
        registerAllVariants(templates, "CIA_SOLO_NEIGHBOUR_", "solo {neighbor}");
        registerAllVariants(templates, "CIA_LANDLOCKED_", "landlocked");
        registerAllVariants(templates, "CIA_GEOGRAPHY_NOTE_", "geo {note}");
        registerAllVariants(templates, "CIA_WORLD_HERITAGE_COUNT_", "heritage {count}");
        registerAllVariants(templates, "CIA_COASTLINE_MASSIVE_", "coastline {distance_km} km");
        registerAllVariants(templates, "CIA_GOVERNMENT_TYPE_", "government {type}");
        registerAllVariants(templates, "CIA_VOTING_AGE_", "voting {voting_age}");
        registerAllVariants(templates, "CIA_NATURALIZATION_YEARS_", "naturalization {rule}");
        IndicatorConfigTestSupport.installTemplates(templates);

        ambiguityCalculator = mock(CIAFactbookAmbiguityCalculator.class);
        when(ambiguityCalculator.compute(any(), anyString())).thenReturn(1);
        lens = new CIAFactbookLens(ambiguityCalculator);
    }

    @AfterEach
    void tearDown() {
        IndicatorConfigTestSupport.clear();
    }

    @Test
    void generatesLeaderHintFromCurrentLeaderText() {
        when(ambiguityCalculator.compute(any(), eq("chief-of-state"))).thenReturn(2);

        List<CandidateHint> hints = lens.generate(context("CHIEF_OF_STATE", "President DONALD TRUMP (since 20 January 2025)"));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_CHIEF_OF_STATE_");
            assertThat(hint.text()).contains("President Donald Trump");
            assertThat(hint.ambiguityCount()).isEqualTo(2);
        });
    }

    @Test
    void generatesSingleNeighborHintFromBorderText() {
        when(ambiguityCalculator.compute(any(), eq("solo-neighbour"))).thenReturn(3);

        List<CandidateHint> hints = lens.generate(context("BORDER_COUNTRIES", "France 623 km"));

        assertThat(hints).hasSize(1);
        assertThat(hints.getFirst().text()).contains("France");
        assertThat(hints.getFirst().ambiguityCount()).isEqualTo(3);
    }

    @Test
    void generatesNeighborListHintForThreeNeighbors() {
        when(ambiguityCalculator.compute(any(), eq("border-countries-list"))).thenReturn(4);

        List<CandidateHint> hints = lens.generate(context("BORDER_COUNTRIES", "France 623 km; Spain 640 km; Italy 488 km"));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_BORDER_COUNTRIES_");
            assertThat(hint.text()).contains("France, Spain, Italy");
            assertThat(hint.ambiguityCount()).isEqualTo(4);
        });
    }

    @Test
    void generatesLandlockedHintFromZeroCoastline() {
        when(ambiguityCalculator.isVerifiedLandlocked(any())).thenReturn(true);
        when(ambiguityCalculator.compute(any(), eq("landlocked-coast"))).thenReturn(5);

        List<CandidateHint> hints = lens.generate(numericContext("COASTLINE", 0));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_LANDLOCKED_");
            assertThat(hint.ambiguityCount()).isEqualTo(5);
        });
    }

    @Test
    void generatesGeographyNoteOnlyForInterestingText() {
        when(ambiguityCalculator.compute(any(), eq("geography-note"))).thenReturn(2);

        List<CandidateHint> strong = lens.generate(context("GEOGRAPHY_NOTE", "Contains one of the world's driest deserts."));
        List<CandidateHint> weak = lens.generate(context("GEOGRAPHY_NOTE", "Mostly flat lowland with some hills."));

        assertThat(strong).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_GEOGRAPHY_NOTE_");
            assertThat(hint.ambiguityCount()).isEqualTo(2);
        });
        assertThat(weak).isEmpty();
    }

    @Test
    void generatesWorldHeritageCountHint() {
        when(ambiguityCalculator.compute(any(), eq("world-heritage-count"))).thenReturn(4);

        List<CandidateHint> hints = lens.generate(numericContext("WORLD_HERITAGE_SITES_COUNT", 7));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_WORLD_HERITAGE_COUNT_");
            assertThat(hint.text()).contains("7");
            assertThat(hint.ambiguityCount()).isEqualTo(4);
        });
    }

    @Test
    void generatesNoHintForZeroWorldHeritageSites() {
        List<CandidateHint> hints = lens.generate(numericContext("WORLD_HERITAGE_SITES_COUNT", 0));

        assertThat(hints).isEmpty();
    }

    @Test
    void doesNotInferLandlockedFromMissingBorderCountries() {
        List<CandidateHint> hints = lens.generate(context("BORDER_COUNTRIES", "none"));

        assertThat(hints).isEmpty();
    }

    @Test
    void doesNotGenerateLandlockedHintWhenCrossCheckFails() {
        when(ambiguityCalculator.isVerifiedLandlocked(any())).thenReturn(false);

        assertThat(lens.generate(numericContext("COASTLINE", 0))).isEmpty();
    }

    @Test
    void skipsWidelySharedGovernmentTypeAndVotingAge() {
        when(ambiguityCalculator.compute(any(), eq("government-type"))).thenReturn(60);
        when(ambiguityCalculator.compute(any(), eq("voting-age"))).thenReturn(168);

        assertThat(lens.generate(context("GOVERNMENT_TYPE", "presidential republic"))).isEmpty();
        assertThat(lens.generate(context("VOTING_AGE", "18 years of age; universal"))).isEmpty();
    }

    @Test
    void retainsDistinctiveVotingRuleButRejectsMissingNaturalizationValue() {
        when(ambiguityCalculator.compute(any(), eq("voting-age"))).thenReturn(2);

        assertThat(lens.generate(context("VOTING_AGE", "16 years of age; universal")))
                .singleElement().satisfies(hint -> assertThat(hint.text()).contains("16 years"));
        assertThat(lens.generate(context("NATURALIZATION_YEARS", "na"))).isEmpty();
        assertThat(lens.generate(context("NATURALIZATION_YEARS", "unknown"))).isEmpty();
    }

    @Test
    void generatesNoHintForEmptyBorderText() {
        List<CandidateHint> hints = lens.generate(context("BORDER_COUNTRIES", ""));

        assertThat(hints).isEmpty();
    }

    @Test
    void generatesMassiveCoastlineHint() {
        when(ambiguityCalculator.compute(any(), eq("coastline-massive"))).thenReturn(6);

        List<CandidateHint> hints = lens.generate(numericContext("COASTLINE", 25000));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_COASTLINE_MASSIVE_");
            assertThat(hint.ambiguityCount()).isEqualTo(6);
        });
    }

    @Test
    void generatesNoHintForModerateCoastline() {
        List<CandidateHint> hints = lens.generate(numericContext("COASTLINE", 5000));

        assertThat(hints).isEmpty();
    }

    @Test
    void generatesCapitalHintUsingCleanedCapitalName() {
        when(ambiguityCalculator.compute(any(), eq("capital-city"))).thenReturn(2);

        List<CandidateHint> hints = lens.generate(context("CAPITAL_CITY", "Tirana (Tirane)"));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_CAPITAL_CITY_");
            assertThat(hint.text()).contains("Tirana");
            assertThat(hint.text()).doesNotContain("Tirane");
            assertThat(hint.ambiguityCount()).isEqualTo(2);
        });
    }

    @Test
    void generatesHistoricalIndependenceHint() {
        when(ambiguityCalculator.compute(any(), eq("independence"))).thenReturn(6);

        List<CandidateHint> hints = lens.generate(
                context("INDEPENDENCE", "4 July 1776 (declared independence from Great Britain); 3 September 1783 (recognized by Great Britain)")
        );

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_INDEPENDENCE_");
            assertThat(hint.type()).isEqualTo(io.clueterra.questions.entity.enums.HintType.HISTORICAL);
            assertThat(hint.text()).contains("4 July 1776");
            assertThat(hint.text()).contains("3 September 1783");
            assertThat(hint.text()).doesNotContain(";");
            assertThat(hint.ambiguityCount()).isEqualTo(6);
        });
    }

    @Test
    void generatesLegalSystemHintWithoutSemicolonPunctuation() {
        when(ambiguityCalculator.compute(any(), eq("legal-system"))).thenReturn(4);

        List<CandidateHint> hints = lens.generate(
                context("LEGAL_SYSTEM", "common law system based on English common law at the federal level; state legal systems based on common law")
        );

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_LEGAL_SYSTEM_");
            assertThat(hint.text()).contains("common law system based on English common law at the federal level");
            assertThat(hint.text()).contains("state legal systems based on common law");
            assertThat(hint.text()).doesNotContain(";");
            assertThat(hint.ambiguityCount()).isEqualTo(4);
        });
    }

    @Test
    void generatesCurrencyHintWithoutUsdSuffixNoise() {
        when(ambiguityCalculator.compute(any(), eq("currency"))).thenReturn(3);

        List<CandidateHint> hints = lens.generate(context("CURRENCY", "euros (EUR) per US dollar -"));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_CURRENCY_");
            assertThat(hint.text()).contains("euros (EUR)");
            assertThat(hint.text()).doesNotContain("per US dollar");
            assertThat(hint.ambiguityCount()).isEqualTo(3);
        });
    }

    @Test
    void generatesCitizenshipByBirthYesHintWithoutPlaceholderText() {
        when(ambiguityCalculator.compute(any(), eq("citizenship-birth-yes"))).thenReturn(5);

        List<CandidateHint> hints = lens.generate(context("CITIZENSHIP_BY_BIRTH", "yes"));

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("CIA_CITIZENSHIP_BIRTH_YES_");
            assertThat(hint.text()).isEqualTo("birth yes");
            assertThat(hint.ambiguityCount()).isEqualTo(5);
        });
    }

    @Test
    void invalidateCacheDelegatesToAmbiguityCalculator() {
        lens.invalidateCache();

        verify(ambiguityCalculator).invalidateCache();
    }

    private StatisticContext context(String indicatorCode, String valueText) {
        return context(indicatorCode, null, valueText);
    }

    private StatisticContext numericContext(String indicatorCode, int value) {
        return context(indicatorCode, BigDecimal.valueOf(value), null);
    }

    private StatisticContext context(String indicatorCode, BigDecimal value, String valueText) {
        Country country = Country.builder()
                .id(3L)
                .isoAlpha2("ES")
                .isoAlpha3("ESP")
                .nameEn("Spain")
                .region("Europe")
                .subregion("Southern Europe")
                .build();

        CountryStatistic statistic = CountryStatistic.builder()
                .countryId(3L)
                .source(DataSource.CIA_FACTBOOK)
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

    private void registerAllVariants(Map<String, String> templates, String baseCode, String template) {
        for (int i = 1; i <= 3; i++) {
            templates.put(baseCode + i, template);
        }
    }
}
