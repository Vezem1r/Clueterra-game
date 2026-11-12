package io.clueterra.questions.clients.common;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfigTestSupport;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplatedHintBuilderTest {

    @AfterEach
    void tearDown() {
        IndicatorConfigTestSupport.clear();
    }

    @Test
    void buildTemplatedCandidate_returnsEmptyWhenTemplateIsMissing() {
        IndicatorConfigTestSupport.installTemplates(Map.of());

        assertThat(TemplatedHintBuilder.buildTemplatedCandidate(
                "UNKNOWN_TEMPLATE",
                Map.of("country", "Japan"),
                HintType.ABSOLUTE_FACT,
                Category.GEOGRAPHY,
                DataSource.MANUAL,
                2026,
                10,
                new BigDecimal("0.90"),
                1,
                "test-lens"
        )).isEmpty();
    }

    @Test
    void buildTemplatedCandidate_rendersTemplateAndCopiesMetadata() {
        IndicatorConfigTestSupport.installTemplates(Map.of(
                "TEST_TEMPLATE", "Mapped fact: {label} = {value}"
        ));

        CandidateHint hint = TemplatedHintBuilder.buildTemplatedCandidate(
                "TEST_TEMPLATE",
                "SOURCE_INDICATOR",
                Map.of("label", "population", "value", "125M"),
                HintType.ABSOLUTE_FACT,
                Category.DEMOGRAPHICS,
                DataSource.WORLD_BANK,
                2026,
                22,
                new BigDecimal("0.94"),
                2,
                "wb-lens",
                "https://img.test/hint.png"
        ).orElseThrow();

        assertThat(hint.text()).isEqualTo("Mapped fact: population = 125M");
        assertThat(hint.sourceIndicator()).isEqualTo("SOURCE_INDICATOR");
        assertThat(hint.source()).isEqualTo(DataSource.WORLD_BANK);
        assertThat(hint.category()).isEqualTo(Category.DEMOGRAPHICS);
        assertThat(hint.type()).isEqualTo(HintType.ABSOLUTE_FACT);
        assertThat(hint.sourceYear()).isEqualTo(2026);
        assertThat(hint.estimatedDifficulty()).isEqualTo(22);
        assertThat(hint.estimatedQuality()).isEqualByComparingTo("0.94");
        assertThat(hint.ambiguityCount()).isEqualTo(2);
        assertThat(hint.imageUrl()).isEqualTo("https://img.test/hint.png");
    }

    @Test
    void buildDynamicTemplatedCandidate_appliesDynamicScoring() {
        IndicatorConfigTestSupport.installTemplates(Map.of(
                "DYNAMIC_TEMPLATE", "{title} is trending"
        ));
        DynamicDifficultyCalculator calculator = new DynamicDifficultyCalculator();

        CandidateHint hint = TemplatedHintBuilder.buildDynamicTemplatedCandidate(
                calculator,
                "DYNAMIC_TEMPLATE",
                "YT_TREND",
                Map.of("title", "Clip A"),
                HintType.CULTURAL,
                Category.CULTURE,
                DataSource.GLOBAL_BRANDS,
                2026,
                4,
                10,
                new BigDecimal("0.90"),
                "sports-lens",
                null
        ).orElseThrow();

        assertThat(hint.text()).isEqualTo("Clip A is trending");
        assertThat(hint.estimatedDifficulty()).isEqualTo(22);
        assertThat(hint.estimatedQuality()).isEqualByComparingTo("0.77");
    }

    @Test
    void buildConfiguredStaticTemplatedCandidate_usesContextSourceAndCategory() {
        IndicatorConfigTestSupport.installTemplates(Map.of(
                "WB_PROFILE", "{country} has a mapped profile"
        ));

        StatisticContext context = StatisticContext.builder()
                .country(Country.builder().isoAlpha2("AQ").nameEn("Testland").build())
                .statistic(CountryStatistic.builder()
                        .source(DataSource.WORLD_BANK)
                        .indicatorCode("SP.POP.TOTL")
                        .category(Category.ECONOMY)
                        .year(2025)
                        .confidence(new BigDecimal("1.00"))
                        .weight(new BigDecimal("0.90"))
                        .build())
                .totalCountriesWithData(100)
                .totalActiveCountries(100)
                .indicatorDisplayName("population")
                .build();

        CandidateHint hint = TemplatedHintBuilder.buildConfiguredStaticTemplatedCandidate(
                context,
                "WB_PROFILE",
                "SP.POP.TOTL",
                Map.of("country", "Testland"),
                HintType.ABSOLUTE_FACT,
                1,
                20,
                new BigDecimal("0.88"),
                "world-bank",
                null
        ).orElseThrow();

        assertThat(hint.text()).isEqualTo("Testland has a mapped profile");
        assertThat(hint.source()).isEqualTo(DataSource.WORLD_BANK);
        assertThat(hint.category()).isEqualTo(Category.ECONOMY);
        assertThat(hint.sourceYear()).isEqualTo(2025);
        assertThat(hint.sourceIndicator()).isEqualTo("SP.POP.TOTL");
        assertThat(hint.estimatedDifficulty()).isEqualTo(34);
        assertThat(hint.estimatedQuality()).isEqualByComparingTo("0.88");
    }
}
