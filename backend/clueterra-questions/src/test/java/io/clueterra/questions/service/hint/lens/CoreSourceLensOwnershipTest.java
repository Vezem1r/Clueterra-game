package io.clueterra.questions.service.hint.lens;

import io.clueterra.questions.clients.ciafactbook.hintgeneration.CIAFactbookAmbiguityCalculator;
import io.clueterra.questions.clients.ciafactbook.hintgeneration.CIAFactbookLens;
import io.clueterra.questions.clients.geonames.hintgeneration.GeonamesAmbiguityCalculator;
import io.clueterra.questions.clients.geonames.hintgeneration.GeonamesLens;
import io.clueterra.questions.clients.wikidata.hintgeneration.WikidataAmbiguityCalculator;
import io.clueterra.questions.clients.wikidata.hintgeneration.WikidataLens;
import io.clueterra.questions.clients.worldbank.hintgeneration.WorldBankAmbiguityCalculator;
import io.clueterra.questions.clients.worldbank.hintgeneration.WorldBankLens;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.CountryDifficultyAdjuster;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreSourceLensOwnershipTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("ownedIndicatorCases")
    void core_source_lenses_claim_their_own_source_only(String ignored,
                                                        DataSource source,
                                                        String indicatorCode,
                                                        Supplier<HintLens> lensFactory) {
        HintLens lens = lensFactory.get();

        assertThat(lens.lensType()).isEqualTo(LensType.SPECIALIZED_STATIC);
        assertThat(lens.claimedSources()).contains(source);
        assertThat(lens.hasOwnershipRules()).isTrue();
        assertThat(lens.ownsIndicator(source, indicatorCode)).isTrue();
        assertThat(lens.ownsIndicator(otherSource(source), indicatorCode)).isFalse();
        assertThat(lens.ownsIndicator(source, null)).isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("unknownIndicatorCases")
    void core_source_lenses_reject_unknown_indicators(String ignored,
                                                       DataSource source,
                                                       String unknownCode,
                                                       Supplier<HintLens> lensFactory) {
        HintLens lens = lensFactory.get();
        assertThat(lens.ownsIndicator(source, unknownCode)).isFalse();
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> unknownIndicatorCases() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "WORLD_BANK rejects unknown WB indicator",
                        DataSource.WORLD_BANK,
                        "FAKE.WB.INDICATOR",
                        (Supplier<HintLens>) () -> new WorldBankLens(mock(StatisticRepository.class), mock(DynamicDifficultyCalculator.class), mock(WorldBankAmbiguityCalculator.class))
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "WIKIDATA rejects unknown Wikidata indicator",
                        DataSource.WIKIDATA,
                        "FAKE_WIKIDATA_IND",
                        (Supplier<HintLens>) () -> new WikidataLens(mock(StatisticRepository.class), mock(DynamicDifficultyCalculator.class), mock(WikidataAmbiguityCalculator.class), mock(CountryDifficultyAdjuster.class))
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "GEONAMES rejects unknown indicator",
                        DataSource.GEONAMES,
                        "FAKE_GEO_IND",
                        (Supplier<HintLens>) () -> new GeonamesLens(mock(StatisticRepository.class), mock(DynamicDifficultyCalculator.class), mock(GeonamesAmbiguityCalculator.class))
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "CIA_FACTBOOK rejects unknown indicator",
                        DataSource.CIA_FACTBOOK,
                        "FAKE_CIA_IND",
                        (Supplier<HintLens>) () -> new CIAFactbookLens(mock(CIAFactbookAmbiguityCalculator.class))
                )
        );
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> ownedIndicatorCases() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "GEONAMES owns GEO indicators",
                        DataSource.GEONAMES,
                        "GEO_LARGEST_CITY",
                        (Supplier<HintLens>) () -> new GeonamesLens(mock(StatisticRepository.class), mock(DynamicDifficultyCalculator.class), mock(GeonamesAmbiguityCalculator.class))
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "CIA_FACTBOOK owns the entire source",
                        DataSource.CIA_FACTBOOK,
                        "COASTLINE",
                        (Supplier<HintLens>) () -> new CIAFactbookLens(mock(CIAFactbookAmbiguityCalculator.class))
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "WIKIDATA owns its curated indicators",
                        DataSource.WIKIDATA,
                        "WIKIDATA_NATIONAL_ANTHEM",
                        (Supplier<HintLens>) () -> new WikidataLens(mock(StatisticRepository.class), mock(DynamicDifficultyCalculator.class), mock(WikidataAmbiguityCalculator.class), mock(CountryDifficultyAdjuster.class))
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "WORLD_BANK owns comparative indicators",
                        DataSource.WORLD_BANK,
                        "SP.POP.TOTL",
                        (Supplier<HintLens>) () -> new WorldBankLens(mock(StatisticRepository.class), mock(DynamicDifficultyCalculator.class), mock(WorldBankAmbiguityCalculator.class))
                )
        );
    }

    private static DataSource otherSource(DataSource source) {
        return Set.of(
                DataSource.MANUAL,
                DataSource.CIA_FACTBOOK,
                DataSource.GEONAMES,
                DataSource.WIKIDATA,
                DataSource.WORLD_BANK
        ).stream()
                .filter(candidate -> candidate != source)
                .findFirst()
                .orElseThrow();
    }
}
