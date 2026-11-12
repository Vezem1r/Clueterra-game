package io.clueterra.questions.clients.nagerdate.hintgeneration;

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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NagerDateLensTest {

    @AfterEach
    void tearDown() {
        IndicatorConfigTestSupport.clear();
    }

    @Test
    void neverClaimsHolidayIsExclusiveToOneCountry() {
        IndicatorConfigTestSupport.installTemplates(Map.of(
                "HOLIDAY_UNIQUE_1", "Only this country celebrates {name}.",
                "HOLIDAY_UNIQUE_2", "Only this country celebrates {name}.",
                "HOLIDAY_UNIQUE_3", "Only this country celebrates {name}."));
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findBySource(DataSource.NAGER_DATE)).thenReturn(List.of(
                statistic("HOLIDAY_UNIQUE_EN", "Lantern Festival")));

        List<CandidateHint> hints = new NagerDateLens(repository, new DynamicDifficultyCalculator())
                .generate(context());

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).startsWith("HOLIDAY_UNIQUE_");
            assertThat(hint.text()).contains("Lantern Festival", "Southern Europe")
                    .doesNotContain("Only this country", "only country");
        });
    }

    private StatisticContext context() {
        Country country = Country.builder()
                .id(3L).isoAlpha2("ES").isoAlpha3("ESP")
                .nameEn("Spain").region("Europe").subregion("Southern Europe").build();
        CountryStatistic statistic = statistic("HOLIDAY_COUNT", null);
        return new StatisticContext(country, statistic, 120, 180, "HOLIDAY_COUNT", true,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    private CountryStatistic statistic(String code, String valueText) {
        return CountryStatistic.builder()
                .countryId(3L).source(DataSource.NAGER_DATE).indicatorCode(code)
                .indicatorName(code).category(Category.CULTURE).valueText(valueText).year(2026).build();
    }
}
