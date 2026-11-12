package io.clueterra.questions.clients.fastfood.hintgeneration;

import io.clueterra.questions.clients.fastfood.FastFoodConfigLoader;
import io.clueterra.questions.clients.fastfood.dto.FastFoodData;
import io.clueterra.questions.clients.fastfood.dto.ChainInfo;
import io.clueterra.questions.clients.fastfood.dto.CountryFastFood;
import io.clueterra.questions.clients.fastfood.dto.SignatureItem;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
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

class FastFoodLensTest {

    @AfterEach
    void tearDown() {
        IndicatorConfigTestSupport.clear();
    }

    @Test
    void removesUnsupportedMenuExclusivityAndIgnoresInvalidOpeningYear() {
        IndicatorConfigTestSupport.installTemplates(Map.of(
                "FF_SIGNATURE_ITEM_1",
                "The local menu features {item} at {chain}: {note}. You won't find this on the menu anywhere else in the world."));

        CountryFastFood countryData = new CountryFastFood(
                Map.of("mcdonalds", new ChainInfo(1, 0)),
                List.of(new SignatureItem("McDonald's", "rice burger", "made with rice buns")),
                null, List.of(), List.of(), Map.of());
        FastFoodData allData = new FastFoodData(Map.of("ES", countryData));
        FastFoodConfigLoader loader = mock(FastFoodConfigLoader.class);
        when(loader.getCountryData("ES")).thenReturn(Optional.of(countryData));
        when(loader.getData()).thenReturn(allData);

        List<CandidateHint> hints = new FastFoodLens(loader).generate(context());

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).isEqualTo("FF_SIGNATURE_ITEM_1");
            assertThat(hint.text()).contains("rice burger", "made with rice buns")
                    .doesNotContain("anywhere else", "year 0");
        });
    }

    private StatisticContext context() {
        Country country = Country.builder()
                .id(3L).isoAlpha2("ES").isoAlpha3("ESP")
                .nameEn("Spain").region("Europe").subregion("Southern Europe").build();
        CountryStatistic statistic = CountryStatistic.builder()
                .countryId(3L).source(DataSource.FAST_FOOD).indicatorCode("FF_CHAINS_PRESENT")
                .indicatorName("chains present").category(Category.CULTURE).year(2026).build();
        return new StatisticContext(country, statistic, 120, 180, "FF_CHAINS_PRESENT", true,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }
}
