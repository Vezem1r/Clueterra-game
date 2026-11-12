package io.clueterra.questions.clients.wikidata.hintgeneration;

import io.clueterra.questions.entity.CountryStatistic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WikidataAmbiguityCalculatorTest {

    private final WikidataAmbiguityCalculator calculator = new WikidataAmbiguityCalculator();

    @Test
    void plugAmbiguityUsesTheFriendlyItemsActuallyShownToThePlayer() {
        Map<Long, List<CountryStatistic>> statistics = Map.of(
                1L, List.of(statistic("WIKIDATA_ELECTRICAL_PLUG", "BS 1363")),
                2L, List.of(statistic("WIKIDATA_ELECTRICAL_PLUG",
                        "AC power plugs and sockets: British and related types, BS 1363")),
                3L, List.of(statistic("WIKIDATA_ELECTRICAL_PLUG", "BS 1363, Europlug"))
        );

        assertThat(calculator.computeItems(
                "WIKIDATA_ELECTRICAL_PLUG", List.of("Type G"), statistics, 1L))
                .isEqualTo(2);
        assertThat(calculator.computeItems(
                "WIKIDATA_ELECTRICAL_PLUG", List.of("Type G", "Type C"), statistics, 3L))
                .isZero();
    }

    @Test
    void infrastructurePairRequiresBothDrivingSideAndDisplayedPlugItems() {
        Map<Long, List<CountryStatistic>> statistics = Map.of(
                1L, List.of(
                        statistic("WIKIDATA_DRIVING_SIDE", "left"),
                        statistic("WIKIDATA_ELECTRICAL_PLUG", "BS 1363")),
                2L, List.of(
                        statistic("WIKIDATA_DRIVING_SIDE", "left"),
                        statistic("WIKIDATA_ELECTRICAL_PLUG", "BS 1363, Europlug")),
                3L, List.of(
                        statistic("WIKIDATA_DRIVING_SIDE", "right"),
                        statistic("WIKIDATA_ELECTRICAL_PLUG", "BS 1363, Europlug"))
        );

        assertThat(calculator.computePairWithItems(
                "WIKIDATA_DRIVING_SIDE", "left",
                "WIKIDATA_ELECTRICAL_PLUG", List.of("Type G"),
                statistics, 1L))
                .isEqualTo(1);
    }

    private CountryStatistic statistic(String indicatorCode, String value) {
        return CountryStatistic.builder()
                .indicatorCode(indicatorCode)
                .valueText(value)
                .build();
    }
}
