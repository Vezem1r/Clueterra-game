package io.clueterra.questions.clients.budgetprojects;

import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BudgetProjectClientTest {

    @Test
    void readsBudgetSchemaAndCreatesSeparateProjectIndicators() {
        BudgetProjectRepository projectRepository = mock(BudgetProjectRepository.class);
        SourcesProperties sourcesProperties = mock(SourcesProperties.class);
        CountryJdbcRepository countryRepository = mock(CountryJdbcRepository.class);
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        SourceProperties properties = new SourceProperties();
        properties.setEnabled(true);
        when(sourcesProperties.getSource(DataSource.BUDGET_PROJECTS)).thenReturn(properties);
        when(projectRepository.isAvailable()).thenReturn(true);
        when(projectRepository.findProjects(null)).thenReturn(List.of(project("Q1", true), project("Q2", false)));
        when(statisticRepository.findCurrentBySource(DataSource.BUDGET_PROJECTS)).thenReturn(List.of());

        BudgetProjectClient client = new BudgetProjectClient(projectRepository, sourcesProperties,
                countryRepository, statisticRepository, Optional.empty());
        client.init();
        List<RawCountryData> countries = client.fetchAllCountries(FetchProgressListener.NO_OP);

        assertThat(client.isDynamic()).isFalse();
        assertThat(countries).hasSize(1);
        assertThat(countries.getFirst().getStatistics())
                .extracting(statistic -> statistic.indicatorCode())
                .containsExactly(
                        "BUDGET_PROJECT_Q1:PROJECT",
                        "BUDGET_PROJECT_Q1:CATEGORY",
                        "BUDGET_PROJECT_Q1:DIFFICULTY",
                        "BUDGET_PROJECT_Q1:LIFECYCLE",
                        "BUDGET_PROJECT_Q1:OPENING_YEAR",
                        "BUDGET_PROJECT_Q1:COST_USD",
                        "BUDGET_PROJECT_Q1:LENGTH");
        assertThat(countries.getFirst().getStatistics().get(5).value())
                .isEqualByComparingTo("2000000");
    }

    @Test
    void inactiveProjectsProduceEmptyCountrySliceForRetirement() {
        BudgetProjectRepository projectRepository = mock(BudgetProjectRepository.class);
        SourcesProperties sourcesProperties = mock(SourcesProperties.class);
        CountryJdbcRepository countryRepository = mock(CountryJdbcRepository.class);
        SourceProperties properties = new SourceProperties();
        properties.setEnabled(true);
        when(sourcesProperties.getSource(DataSource.BUDGET_PROJECTS)).thenReturn(properties);
        when(projectRepository.isAvailable()).thenReturn(true);
        when(projectRepository.findProjects("US")).thenReturn(List.of(project("Q2", false)));
        when(countryRepository.findByIsoAlpha2("US"))
                .thenReturn(Optional.of(Country.builder().id(1L).isoAlpha2("US").build()));

        BudgetProjectClient client = new BudgetProjectClient(projectRepository, sourcesProperties,
                countryRepository, mock(StatisticRepository.class), Optional.empty());
        client.init();

        assertThat(client.fetchCountryByCode("US")).get()
                .satisfies(country -> assertThat(country.getStatistics()).isEmpty());
    }

    private BudgetProjectSnapshot project(String wikidataId, boolean eligible) {
        return new BudgetProjectSnapshot(wikidataId, "Example Bridge", "US", "United States",
                "BRIDGE", "EASY", "COMPLETED", 2015, BigDecimal.valueOf(2_000_000), "FINAL_COST",
                "LENGTH", BigDecimal.valueOf(1_500), "M", eligible, "image.jpg", "sha",
                "https://example.com/image.jpg", "https://example.com/source", "Author",
                "Photo by Author", "CC BY-SA 4.0", "https://example.com/license");
    }
}
