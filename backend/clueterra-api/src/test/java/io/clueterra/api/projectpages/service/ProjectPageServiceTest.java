package io.clueterra.api.projectpages.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.api.projectpages.repository.ProjectPageCardRow;
import io.clueterra.api.projectpages.repository.ProjectPageCostRow;
import io.clueterra.api.projectpages.repository.ProjectPageDetailRow;
import io.clueterra.api.projectpages.repository.ProjectHubCountryRow;
import io.clueterra.api.projectpages.repository.ProjectPageFactRow;
import io.clueterra.api.projectpages.repository.ProjectPageJdbcRepository;
import io.clueterra.api.projectpages.repository.ProjectPageMetricRow;
import io.clueterra.api.projectpages.repository.ProjectPageMilestoneRow;
import io.clueterra.api.projectpages.repository.ProjectDirectoryFilter;

@ExtendWith(MockitoExtension.class)
class ProjectPageServiceTest {

    private static final int COUNTRY_HUB_MINIMUM_PROJECTS = 8;

    private static final ProjectDirectoryFilter EMPTY_FILTER =
            new ProjectDirectoryFilter(null, null, null, null, null, null);

    @Mock
    private ProjectPageJdbcRepository repository;

    private ProjectPageService service;

    @BeforeEach
    void setUp() {
        service = new ProjectPageService(repository);
    }

    @Test
    void formatsSourceCurrencyAndHistoricalUsdComparisonForCards() {
        when(repository.countPublishedCards(EMPTY_FILTER)).thenReturn(1L);
        when(repository.findPublishedCards(EMPTY_FILTER, 24, 0)).thenReturn(List.of(new ProjectPageCardRow(
                "Q184213",
                "akashi-kaikyo-bridge",
                "Akashi Kaikyō Bridge",
                "Japan",
                "BRIDGE",
                1998,
                new BigDecimal("3823345000"),
                new BigDecimal("500000000000"),
                "JPY",
                "Source-backed bridge cost.",
                "sha1",
                "Akashi Kaikyō Bridge",
                Instant.parse("2026-09-20T10:00:00Z"))));

        ProjectDirectoryPage page = service.findPublishedCards(1, 24, EMPTY_FILTER).orElseThrow();
        ProjectCardView result = page.projects().getFirst();

        assertThat(result.answer()).isEqualTo("¥500 billion");
        assertThat(result.usdEquivalent()).isEqualTo("Approximately $3.82 billion");
        assertThat(result.category()).isEqualTo("Bridge");
        assertThat(result.imageUrl()).isEqualTo("/api/assets/budget/projects/Q184213/sha1");
        assertThat(page.totalProjects()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void rejectsDirectoryPagesPastTheLastPublishedPageWithoutLoadingCards() {
        when(repository.countPublishedCards(EMPTY_FILTER)).thenReturn(25L);

        assertThat(service.findPublishedCards(3, 24, EMPTY_FILTER)).isEmpty();
    }

    @Test
    void rejectsInvertedCostAndYearRanges() {
        ProjectDirectoryFilter invertedCost = new ProjectDirectoryFilter(
                null,
                null,
                new BigDecimal("200"),
                new BigDecimal("100"),
                null,
                null);
        ProjectDirectoryFilter invertedYear = new ProjectDirectoryFilter(null, null, null, null, 2030, 1990);

        assertThatThrownBy(() -> service.findPublishedCards(1, 24, invertedCost))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Minimum project cost cannot exceed maximum project cost");
        assertThatThrownBy(() -> service.findPublishedCards(1, 24, invertedYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Minimum project year cannot exceed maximum project year");
    }

    @Test
    void buildsDetailFactsFromPrimaryMilestonesAndMetrics() {
        ProjectPageDetailRow row = new ProjectPageDetailRow(
                "Q184213",
                "akashi-kaikyo-bridge",
                "How much did the Akashi Kaikyō Bridge cost?",
                "How much did Akashi Kaikyō Bridge cost? | Clueterra",
                "A sufficiently long description for the project page.",
                "Akashi bridge cost",
                "Introduction",
                "Answer detail",
                "Scope",
                "Context",
                "Methodology",
                "WSP",
                "http://www.wspgroup.com/en/project",
                Instant.parse("2026-09-20T10:00:00Z"),
                "Akashi Kaikyō Bridge",
                "JP",
                "Japan",
                "BRIDGE",
                1998,
                new BigDecimal("3823345000"),
                new BigDecimal("500000000000"),
                "JPY",
                1998,
                "FINAL_COST",
                "Bridge itself",
                "COMPLETED",
                "sha1",
                "Akashi Kaikyō Bridge",
                "Photographer",
                "Photo attribution",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/",
                "https://commons.wikimedia.org/wiki/File:Bridge.jpg",
                List.of(new ProjectPageCostRow(
                        "FINAL_COST",
                        new BigDecimal("500000000000"),
                        "JPY",
                        new BigDecimal("3823345000"),
                        1998,
                        true,
                        "Bridge itself",
                        "Euractiv",
                        "https://www.euractiv.com/example")),
                List.of(new ProjectPageMetricRow(
                        "LENGTH", new BigDecimal("3911"), "M",
                        "Official source", "https://example.com/source")),
                List.of(new ProjectPageMilestoneRow(
                        "OPENING", LocalDate.of(1998, 4, 5), null,
                        "Official source", "https://example.com/source")),
                List.of(new ProjectPageFactRow(
                        "Crossing", "Links Kobe and Awaji Island.",
                        "Official source", "https://example.com/source")),
                List.of());
        when(repository.findPublishedBySlug("akashi-kaikyo-bridge")).thenReturn(Optional.of(row));
        when(repository.findHubCountries(COUNTRY_HUB_MINIMUM_PROJECTS)).thenReturn(List.of(
                new ProjectHubCountryRow("JP", "Japan", 31)));

        ProjectDetailView result = service.findPublishedProject("akashi-kaikyo-bridge").orElseThrow();

        assertThat(result.costType()).isEqualTo("Final project cost");
        assertThat(result.costScope()).isEqualTo("Bridge itself");
        assertThat(result.status()).isNull();
        assertThat(result.sourceName()).isEqualTo("WSP");
        assertThat(result.costs()).containsExactly(new ProjectCostView(
                "Final project cost",
                "¥500 billion",
                "Approximately $3.82 billion",
                1998,
                true,
                "Bridge itself",
                "Euractiv",
                "https://www.euractiv.com/example"));
        assertThat(result.metrics()).containsExactly(new ProjectMetricView(
                "Total length", "3,911 m", "Official source", "https://example.com/source"));
        assertThat(result.timeline()).containsExactly(new ProjectMilestoneView(
                "Opened or launched", "5 April 1998", "Official source", "https://example.com/source"));
        assertThat(result.facts()).containsExactly(new ProjectFactView(
                "Crossing", "Links Kobe and Awaji Island.", "Official source", "https://example.com/source"));
        assertThat(result.updatedDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(result.categoryHubPath()).isEqualTo("/projects/most-expensive/bridges");
        assertThat(result.countryHubPath()).isEqualTo("/projects/countries/japan");
        verify(repository).findPublishedBySlug("akashi-kaikyo-bridge");
        verify(repository).findHubCountries(COUNTRY_HUB_MINIMUM_PROJECTS);
        verifyNoMoreInteractions(repository);
    }
}
