package io.clueterra.questions.clients.owid.hintgeneration;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfigTestSupport;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OwidLensTest {

    @AfterEach
    void tearDown() {
        IndicatorConfigTestSupport.clear();
    }

    @Test
    void middleOfPackInternetAndAlcoholValuesDoNotCreateGenericFacts() {
        IndicatorConfigTestSupport.installTemplates(Map.of(
                "OWID_INTERNET_FACT", "{value}% are {tier}.",
                "OWID_ALCOHOL_FACT", "{value} litres is {tier}."));
        List<CountryStatistic> statistics = new ArrayList<>();
        for (long countryId = 1; countryId <= 60; countryId++) {
            statistics.add(statistic(countryId, "OWID_INTERNET_USERS", 100 - countryId));
            statistics.add(statistic(countryId, "OWID_ALCOHOL_CONSUMPTION", 60 - countryId));
        }
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findBySource(DataSource.OUR_WORLD_IN_DATA)).thenReturn(statistics);
        OwidLens lens = new OwidLens(repository, new DynamicDifficultyCalculator());

        assertThat(lens.generate(context(statistics.get(58)))).isEmpty();
        assertThat(lens.generate(context(statistics.get(59)))).isEmpty();
    }

    private CountryStatistic statistic(long countryId, String code, long value) {
        return CountryStatistic.builder()
                .countryId(countryId).source(DataSource.OUR_WORLD_IN_DATA)
                .indicatorCode(code).indicatorName(code).category(Category.CULTURE)
                .value(BigDecimal.valueOf(value)).year(2026).build();
    }

    private StatisticContext context(CountryStatistic statistic) {
        Country country = Country.builder()
                .id(30L).isoAlpha2("ES").isoAlpha3("ESP")
                .nameEn("Spain").region("Europe").subregion("Southern Europe").build();
        return new StatisticContext(country, statistic, 120, 180,
                statistic.getIndicatorCode(), true,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }
}
