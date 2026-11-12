package io.clueterra.api.projectpages.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.api.projectpages.repository.ProjectHubCardRow;
import io.clueterra.api.projectpages.repository.ProjectHubCategoryRow;
import io.clueterra.api.projectpages.repository.ProjectHubCountryRow;
import io.clueterra.api.projectpages.repository.ProjectPageJdbcRepository;

@ExtendWith(MockitoExtension.class)
class ProjectPageHubServiceTest {

    private static final int COUNTRY_HUB_MINIMUM_PROJECTS = 8;

    @Mock
    private ProjectPageJdbcRepository repository;

    private ProjectPageService service;

    @BeforeEach
    void setUp() {
        service = new ProjectPageService(repository);
    }

    @Test
    void categoryHubsSkipEmptyCategoriesAndFollowTheFixedOrder() {
        when(repository.countHubCategories()).thenReturn(List.of(
                new ProjectHubCategoryRow("STADIUM", 3),
                new ProjectHubCategoryRow("BRIDGE", 5)));

        List<ProjectHubLink> hubs = service.findCategoryHubs();

        assertThat(hubs).extracting(ProjectHubLink::path).containsExactly(
                "/projects/most-expensive/bridges",
                "/projects/most-expensive/stadiums");
        assertThat(hubs).extracting(ProjectHubLink::projectCount).containsExactly(5L, 3L);
    }

    @Test
    void countryHubsUseAsciiSlugsOfTheCountryName() {
        when(repository.findHubCountries(COUNTRY_HUB_MINIMUM_PROJECTS)).thenReturn(List.of(
                new ProjectHubCountryRow("TR", "Türkiye", 21),
                new ProjectHubCountryRow("HK", "Hong Kong SAR China", 8)));

        assertThat(service.findCountryHubs()).extracting(ProjectHubLink::path).containsExactly(
                "/projects/countries/turkiye",
                "/projects/countries/hong-kong-sar-china");
    }

    @Test
    void categoryHubRanksProjectsAndMarksEstimatesAndConstruction() {
        when(repository.countHubCategories()).thenReturn(List.of(new ProjectHubCategoryRow("STADIUM", 2)));
        when(repository.findHubCountries(COUNTRY_HUB_MINIMUM_PROJECTS)).thenReturn(List.of());
        when(repository.findHubCards("STADIUM", null, 25)).thenReturn(List.of(
                stadium("wembley-stadium", "Wembley Stadium", "1500000000", "1200000000", "GBP", "REPORTED_COST", "COMPLETED"),
                stadium("new-stadium", "New Stadium", "900000000", "900000000", "USD", "CURRENT_ESTIMATE", "UNDER_CONSTRUCTION")));

        ProjectHubPage hub = service.findCategoryHub("stadiums").orElseThrow();

        assertThat(hub.kind()).isEqualTo(ProjectHubKind.CATEGORY);
        assertThat(hub.path()).isEqualTo("/projects/most-expensive/stadiums");
        assertThat(hub.totalProjects()).isEqualTo(2);
        assertThat(hub.projects()).extracting(ProjectHubEntry::rank).containsExactly(1, 2);
        assertThat(hub.projects().get(0).costUsd()).isEqualTo("$1.5 billion");
        assertThat(hub.projects().get(0).answer()).isEqualTo("£1.2 billion");
        assertThat(hub.projects().get(0).estimate()).isFalse();
        assertThat(hub.projects().get(1).estimate()).isTrue();
        assertThat(hub.projects().get(0).status()).isNull();
        assertThat(hub.projects().get(1).status()).isEqualTo("Under construction");
    }

    @Test
    void unknownCategorySlugHasNoHub() {
        assertThat(service.findCategoryHub("castles")).isEmpty();
        verify(repository, never()).findHubCards(anyString(), anyString(), anyInt());
    }

    @Test
    void countryHubResolvesTheCountryFromItsSlug() {
        when(repository.findHubCountries(COUNTRY_HUB_MINIMUM_PROJECTS)).thenReturn(List.of(
                new ProjectHubCountryRow("TR", "Türkiye", 21)));
        when(repository.countHubCategories()).thenReturn(List.of());
        when(repository.findHubCards(null, "TR", 30)).thenReturn(List.of());

        ProjectHubPage hub = service.findCountryHub("turkiye").orElseThrow();

        assertThat(hub.countryCode()).isEqualTo("TR");
        assertThat(hub.countryName()).isEqualTo("Türkiye");
        assertThat(hub.totalProjects()).isEqualTo(21);
        assertThat(service.findCountryHub("atlantis")).isEmpty();
    }

    @Test
    void hubPathsListTheOverallRankingCategoriesAndCountries() {
        when(repository.countHubCategories()).thenReturn(List.of(new ProjectHubCategoryRow("DAM", 4)));
        when(repository.findHubCountries(COUNTRY_HUB_MINIMUM_PROJECTS)).thenReturn(List.of(
                new ProjectHubCountryRow("JP", "Japan", 31)));

        assertThat(service.findHubPaths()).containsExactly(
                "/projects/most-expensive",
                "/projects/most-expensive/dams",
                "/projects/countries/japan");
    }

    private ProjectHubCardRow stadium(
            String slug,
            String name,
            String costUsd,
            String sourceAmount,
            String currency,
            String costKind,
            String lifecycleStatus) {
        return new ProjectHubCardRow(
                "Q1",
                slug,
                name,
                "United Kingdom",
                "STADIUM",
                2007,
                new BigDecimal(costUsd),
                new BigDecimal(sourceAmount),
                currency,
                costKind,
                null,
                lifecycleStatus,
                null,
                "sha1",
                "Stadium photo");
    }
}
