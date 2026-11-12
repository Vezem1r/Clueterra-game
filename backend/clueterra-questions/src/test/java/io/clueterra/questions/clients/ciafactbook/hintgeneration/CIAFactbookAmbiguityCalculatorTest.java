package io.clueterra.questions.clients.ciafactbook.hintgeneration;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CIAFactbookAmbiguityCalculatorTest {

    @Test
    void computesSoloNeighbourAmbiguityFromMatchingNeighbor() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                textStat(1L, "BORDER_COUNTRIES", "France 623 km"),
                textStat(2L, "BORDER_COUNTRIES", "France 510 km"),
                textStat(3L, "BORDER_COUNTRIES", "Germany 700 km")
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(context(textStat(1L, "BORDER_COUNTRIES", "France 623 km")), "solo-neighbour");

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesTwoNeighboursAmbiguityIgnoringOrder() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                textStat(1L, "BORDER_COUNTRIES", "France 623 km; Spain 640 km"),
                textStat(2L, "BORDER_COUNTRIES", "Spain 500 km; France 600 km"),
                textStat(3L, "BORDER_COUNTRIES", "France 500 km; Italy 400 km")
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(
                context(textStat(1L, "BORDER_COUNTRIES", "France 623 km; Spain 640 km")),
                "two-neighbours"
        );

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesBorderCountryListAmbiguityFromExactNeighborSet() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                textStat(1L, "BORDER_COUNTRIES", "France 623 km; Spain 640 km; Italy 488 km"),
                textStat(2L, "BORDER_COUNTRIES", "Italy 500 km; France 600 km; Spain 700 km"),
                textStat(3L, "BORDER_COUNTRIES", "France 500 km; Germany 400 km; Italy 200 km")
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(
                context(textStat(1L, "BORDER_COUNTRIES", "France 623 km; Spain 640 km; Italy 488 km")),
                "border-countries-list"
        );

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesLongBorderAmbiguityWithinRelativeBand() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                textStat(1L, "BORDER_COUNTRIES", "France 2100 km; Spain 640 km"),
                textStat(2L, "BORDER_COUNTRIES", "France 2250 km"),
                textStat(3L, "BORDER_COUNTRIES", "Germany 2100 km")
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(
                context(textStat(1L, "BORDER_COUNTRIES", "France 2100 km; Spain 640 km")),
                "long-border"
        );

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesMassiveCoastlineAmbiguityWithinBand() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                numericStat(1L, "COASTLINE", 25000),
                numericStat(2L, "COASTLINE", 24850),
                numericStat(3L, "COASTLINE", 18000)
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(context(numericStat(1L, "COASTLINE", 25000)), "coastline-massive");

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesLandlockedAmbiguityFromZeroCoastline() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                numericStat(1L, "COASTLINE", 0),
                numericStat(1L, "LAND_BOUNDARIES_TOTAL", 1200),
                numericStat(2L, "COASTLINE", 0),
                numericStat(2L, "LAND_BOUNDARIES_TOTAL", 800),
                numericStat(3L, "COASTLINE", 300)
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(context(numericStat(1L, "COASTLINE", 0)), "landlocked-coast");

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesFlagDescriptionAmbiguityAfterCleaning() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                textStat(1L, "FLAG_DESCRIPTION", "description: three horizontal stripes with a yellow star"),
                textStat(2L, "FLAG_DESCRIPTION", "three horizontal stripes with a yellow star"),
                textStat(3L, "FLAG_DESCRIPTION", "description: red field with white cross")
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(
                context(textStat(1L, "FLAG_DESCRIPTION", "description: three horizontal stripes with a yellow star")),
                "flag-description"
        );

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesWorldHeritageListAmbiguityFromCompactedList() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                textStat(1L, "WORLD_HERITAGE_SITES", "Old Town; Mountain Sanctuary; River Reserve"),
                textStat(2L, "WORLD_HERITAGE_SITES", "Old Town; Mountain Sanctuary"),
                textStat(3L, "WORLD_HERITAGE_SITES", "Desert Park; River Reserve")
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(
                context(textStat(1L, "WORLD_HERITAGE_SITES", "Old Town; Mountain Sanctuary; River Reserve")),
                "world-heritage"
        );

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesWorldHeritageCountAmbiguityAsExactNumericMatch() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                numericStat(1L, "WORLD_HERITAGE_SITES_COUNT", 7),
                numericStat(2L, "WORLD_HERITAGE_SITES_COUNT", 7),
                numericStat(3L, "WORLD_HERITAGE_SITES_COUNT", 4)
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(
                context(numericStat(1L, "WORLD_HERITAGE_SITES_COUNT", 7)),
                "world-heritage-count"
        );

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesAreaComparativeAmbiguityFromCompactedText() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                textStat(1L, "AREA_COMPARATIVE", "slightly smaller than Texas"),
                textStat(2L, "AREA_COMPARATIVE", "slightly smaller than Texas"),
                textStat(3L, "AREA_COMPARATIVE", "about twice the size of Belgium")
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(
                context(textStat(1L, "AREA_COMPARATIVE", "slightly smaller than Texas")),
                "area-comparative"
        );

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesCleanTextAmbiguityForHighestPoint() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                textStat(1L, "HIGHEST_POINT", "Pico Cristal 2,100 m"),
                textStat(2L, "HIGHEST_POINT", "Pico Cristal 2,100 m"),
                textStat(3L, "HIGHEST_POINT", "Mount Azure 4,050 m")
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(
                context(textStat(1L, "HIGHEST_POINT", "Pico Cristal 2,100 m")),
                "highest-point"
        );

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesListBranchAmbiguityForExportPartners() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                textStat(1L, "EXPORT_PARTNERS", "Germany 22%; France 18%; Spain 10%"),
                textStat(2L, "EXPORT_PARTNERS", "Germany 30%; France 12%; Spain 9%"),
                textStat(3L, "EXPORT_PARTNERS", "China 35%; Japan 14%; Korea 11%")
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(
                context(textStat(1L, "EXPORT_PARTNERS", "Germany 22%; France 18%; Spain 10%")),
                "export-partners"
        );

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void computesMajorCitiesAmbiguityUsingCompactedCityList() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK)).thenReturn(List.of(
                textStat(1L, "MAJOR_CITIES", "MADRID -- 6.7 million; BARCELONA -- 5.5 million"),
                textStat(2L, "MAJOR_CITIES", "Madrid -- 3.2 million; Barcelona -- 1.6 million"),
                textStat(3L, "MAJOR_CITIES", "Lima -- 4.0 million; Cusco -- 0.4 million")
        ));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);

        int ambiguity = calculator.compute(
                context(textStat(1L, "MAJOR_CITIES", "MADRID -- 6.7 million; BARCELONA -- 5.5 million")),
                "major-cities"
        );

        assertThat(ambiguity).isEqualTo(2);
    }

    @Test
    void invalidateCacheForcesReloadFromRepository() {
        StatisticRepository repository = mock(StatisticRepository.class);
        when(repository.findCurrentBySource(DataSource.CIA_FACTBOOK))
                .thenReturn(List.of(
                        numericStat(1L, "COASTLINE", 25000),
                        numericStat(2L, "COASTLINE", 25000)
                ))
                .thenReturn(List.of(numericStat(1L, "COASTLINE", 25000)));

        CIAFactbookAmbiguityCalculator calculator = new CIAFactbookAmbiguityCalculator(repository);
        StatisticContext context = context(numericStat(1L, "COASTLINE", 25000));

        assertThat(calculator.compute(context, "coastline-massive")).isEqualTo(2);

        calculator.invalidateCache();

        assertThat(calculator.compute(context, "coastline-massive")).isEqualTo(1);
    }

    private StatisticContext context(CountryStatistic statistic) {
        Country country = Country.builder()
                .id(statistic.getCountryId())
                .isoAlpha2("AA")
                .isoAlpha3("AAA")
                .nameEn("Country " + statistic.getCountryId())
                .region("Region")
                .subregion("Subregion")
                .build();

        return StatisticContext.builder()
                .country(country)
                .statistic(statistic)
                .totalCountriesWithData(200)
                .totalActiveCountries(200)
                .indicatorDisplayName(statistic.getIndicatorCode())
                .isHigherBetter(true)
                .build();
    }

    private CountryStatistic textStat(Long countryId, String indicatorCode, String valueText) {
        return CountryStatistic.builder()
                .countryId(countryId)
                .source(DataSource.CIA_FACTBOOK)
                .indicatorCode(indicatorCode)
                .indicatorName(indicatorCode)
                .category(Category.GEOGRAPHY)
                .valueText(valueText)
                .year(2026)
                .build();
    }

    private CountryStatistic numericStat(Long countryId, String indicatorCode, int value) {
        return CountryStatistic.builder()
                .countryId(countryId)
                .source(DataSource.CIA_FACTBOOK)
                .indicatorCode(indicatorCode)
                .indicatorName(indicatorCode)
                .category(Category.GEOGRAPHY)
                .value(BigDecimal.valueOf(value))
                .year(2026)
                .build();
    }
}
