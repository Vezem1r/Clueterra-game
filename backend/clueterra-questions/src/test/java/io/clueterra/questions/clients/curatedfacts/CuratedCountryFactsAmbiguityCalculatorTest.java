package io.clueterra.questions.clients.curatedfacts;

import io.clueterra.questions.entity.CountryStatistic;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CuratedCountryFactsAmbiguityCalculatorTest {

    private final CuratedCountryFactsAmbiguityCalculator calculator =
            new CuratedCountryFactsAmbiguityCalculator();

    @Test
    void displayedListItemsMatchLongerStoredLists() {
        Map<Long, List<CountryStatistic>> statistics = Map.of(
                1L, List.of(statistic(CuratedCountryFactIndicator.SIGNATURE_DISH, "Couscous")),
                2L, List.of(statistic(CuratedCountryFactIndicator.SIGNATURE_DISH, "Couscous, Rechta")),
                3L, List.of(statistic(CuratedCountryFactIndicator.SIGNATURE_DISH, "Couscous, Brik")),
                4L, List.of(statistic(CuratedCountryFactIndicator.SIGNATURE_DISH, "Kabsa")));

        assertThat(calculator.compute(
                CuratedCountryFactIndicator.SIGNATURE_DISH.getCode(),
                "Couscous",
                statistics,
                1L)).isEqualTo(2);
    }

    @Test
    void groupedAmbiguityRequiresEveryDisplayedFact() {
        Map<Long, List<CountryStatistic>> statistics = Map.of(
                1L, List.of(
                        statistic(CuratedCountryFactIndicator.SIGNATURE_DISH, "Sadza"),
                        statistic(CuratedCountryFactIndicator.TRADITIONAL_INSTRUMENT, "Mbira")),
                2L, List.of(
                        statistic(CuratedCountryFactIndicator.SIGNATURE_DISH, "Sadza, Stew"),
                        statistic(CuratedCountryFactIndicator.TRADITIONAL_INSTRUMENT, "Mbira")),
                3L, List.of(
                        statistic(CuratedCountryFactIndicator.SIGNATURE_DISH, "Sadza"),
                        statistic(CuratedCountryFactIndicator.TRADITIONAL_INSTRUMENT, "Drums")));
        Map<String, String> displayedFacts = new LinkedHashMap<>();
        displayedFacts.put(CuratedCountryFactIndicator.SIGNATURE_DISH.getCode(), "Sadza");
        displayedFacts.put(CuratedCountryFactIndicator.TRADITIONAL_INSTRUMENT.getCode(), "Mbira");

        assertThat(calculator.computeCombination(displayedFacts, statistics, 1L)).isEqualTo(1);
    }

    private CountryStatistic statistic(CuratedCountryFactIndicator indicator, String value) {
        return CountryStatistic.builder()
                .indicatorCode(indicator.getCode())
                .valueText(value)
                .build();
    }
}
