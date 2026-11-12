package io.clueterra.api.projectpages.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.clueterra.api.admin.budgetcontent.controller.AdminBudgetProjectSeoPreviewController;
import io.clueterra.api.config.OAuthProperties;
import io.clueterra.api.filter.RateLimitingFilter;
import io.clueterra.api.projectpages.repository.ProjectDirectoryFilter;
import io.clueterra.api.projectpages.repository.ProjectSitemapRow;
import io.clueterra.api.projectpages.service.ProjectCardView;
import io.clueterra.api.projectpages.service.ProjectDetailView;
import io.clueterra.api.projectpages.service.ProjectDirectoryPage;
import io.clueterra.api.projectpages.service.ProjectFactView;
import io.clueterra.api.projectpages.service.ProjectImageView;
import io.clueterra.api.projectpages.service.ProjectHubEntry;
import io.clueterra.api.projectpages.service.ProjectHubKind;
import io.clueterra.api.projectpages.service.ProjectHubPage;
import io.clueterra.api.projectpages.service.ProjectPageService;
import io.clueterra.api.projectpages.service.ProjectRankingScope;
import io.clueterra.api.security.GuestTokenFilter;
import io.clueterra.api.security.JwtAuthenticationFilter;
import io.clueterra.api.security.LegalAcceptanceFilter;

@WebMvcTest({ProjectPageController.class, AdminBudgetProjectSeoPreviewController.class})
@AutoConfigureMockMvc(addFilters = false)
class ProjectPageControllerTest {

    private static final ProjectDirectoryFilter EMPTY_FILTER =
            new ProjectDirectoryFilter(null, null, null, null, null, null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectPageService projectPageService;

    @MockitoBean
    private OAuthProperties oauthProperties;

    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;

    @MockitoBean
    private GuestTokenFilter guestTokenFilter;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private LegalAcceptanceFilter legalAcceptanceFilter;

    @BeforeEach
    void setUp() {
        when(oauthProperties.getFrontendRedirectUrl()).thenReturn("https://clueterra.com");
    }

    @Test
    void returnsPublishedProjectDirectoryAsJson() throws Exception {
        when(projectPageService.findPublishedCards(1, 24, EMPTY_FILTER))
                .thenReturn(Optional.of(directory(2, 25)));

        mockMvc.perform(get("/api/project-pages/data"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(jsonPath("$.totalProjects").value(25))
                .andExpect(jsonPath("$.projects[0].slug").value("cassini-huygens"))
                .andExpect(jsonPath("$.projects[0].imageUrl").value("/api/assets/budget/projects/Q165585/sha1"));
    }

    @Test
    void forwardsSearchCostAndYearFilters() throws Exception {
        ProjectDirectoryFilter filter = new ProjectDirectoryFilter(
                "rail",
                "METRO_RAIL",
                new BigDecimal("1000000"),
                new BigDecimal("2500000000"),
                1990,
                2030);
        when(projectPageService.findPublishedCards(1, 24, filter))
                .thenReturn(Optional.of(directory(1, 1)));

        mockMvc.perform(get("/api/project-pages/data")
                        .param("q", "rail")
                        .param("category", "METRO_RAIL")
                        .param("minCost", "1000000")
                        .param("maxCost", "2500000000")
                        .param("yearFrom", "1990")
                        .param("yearTo", "2030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProjects").value(1));

        verify(projectPageService).findPublishedCards(1, 24, filter);
    }

    @Test
    void rejectsInvalidDirectoryFilterValues() throws Exception {
        mockMvc.perform(get("/api/project-pages/data").param("minCost", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/project-pages/data").param("yearFrom", "10000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/project-pages/data").param("category", "CASTLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returnsNotFoundForDirectoryPagePastTheEnd() throws Exception {
        when(projectPageService.findPublishedCards(99, 24, EMPTY_FILTER)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/project-pages/data?page=99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project directory page not found"));
    }

    @Test
    void returnsPublishedProjectAndRelatedCardsAsJson() throws Exception {
        ProjectDetailView project = detail();
        when(projectPageService.findPublishedProject("cassini-huygens")).thenReturn(Optional.of(project));
        when(projectPageService.findRelated(project, 3)).thenReturn(List.of(card()));

        mockMvc.perform(get("/api/project-pages/data/cassini-huygens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.seoTitle").value("How much did Cassini–Huygens cost? | Clueterra"))
                .andExpect(jsonPath("$.project.sourceUrl").value("https://science.nasa.gov/mission/cassini/quick-facts/"))
                .andExpect(jsonPath("$.relatedProjects[0].slug").value("cassini-huygens"));
    }

    @Test
    void returnsNotFoundForUnknownOrUnpublishedSlug() throws Exception {
        when(projectPageService.findPublishedProject("missing-project")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/project-pages/data/missing-project"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Published project page not found"));
    }

    @Test
    void returnsPrivateDraftPreviewDataWithoutCaching() throws Exception {
        ProjectDetailView project = detail();
        when(projectPageService.findProjectPreview("Q165585")).thenReturn(Optional.of(project));
        when(projectPageService.findRelated(project, 3)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/budget-content/seo-pages/Q165585/preview-data"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.project.wikidataId").value("Q165585"));
    }

    @Test
    void sitemapCombinesStaticAndDatabaseBackedPages() throws Exception {
        when(projectPageService.findSitemapRows()).thenReturn(List.of(
                new ProjectSitemapRow("cassini-huygens", Instant.parse("2026-09-20T10:00:00Z"))));

        mockMvc.perform(get("/api/project-pages/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<loc>https://clueterra.com/games</loc>")))
                .andExpect(content().string(containsString("<loc>https://clueterra.com/games/rivers/how-to</loc>")))
                .andExpect(content().string(containsString("<loc>https://clueterra.com/games/country-silhouettes</loc>")))
                .andExpect(content().string(containsString("<loc>https://clueterra.com/games/country-flags</loc>")))
                .andExpect(content().string(containsString("<loc>https://clueterra.com/ru/games/country-flags</loc>")))
                .andExpect(content().string(containsString("<loc>https://clueterra.com/cs/games/rivers/how-to</loc>")))
                .andExpect(content().string(containsString("<loc>https://clueterra.com/projects</loc>")))
                .andExpect(content().string(containsString("<loc>https://clueterra.com/projects/cassini-huygens</loc>")))
                .andExpect(content().string(containsString("<lastmod>2026-09-20</lastmod>")));
    }

    private ProjectCardView card() {
        return new ProjectCardView(
                "cassini-huygens",
                "Cassini–Huygens",
                "United States",
                "Space mission",
                1997,
                "$3.9 billion",
                null,
                "Cassini mission cost details.",
                "/api/assets/budget/projects/Q165585/sha1",
                "Cassini spacecraft",
                LocalDate.of(2026, 9, 20));
    }

    private ProjectDirectoryPage directory(int totalPages, long totalProjects) {
        return new ProjectDirectoryPage(List.of(card()), 1, 24, totalProjects, totalPages, List.of());
    }

    private ProjectDetailView detail() {
        return new ProjectDetailView(
                "Q165585",
                "cassini-huygens",
                "How much did Cassini–Huygens cost? | Clueterra",
                "Cassini mission cost and scope from NASA.",
                "Cassini mission cost",
                "Cassini–Huygens",
                "How much did the Cassini–Huygens mission cost?",
                "United States",
                "Space mission",
                "/projects/most-expensive/space-missions",
                "/projects/countries/united-states",
                1997,
                "$3.9 billion",
                null,
                2017,
                "Reported project cost",
                null,
                null,
                "Cassini explored Saturn.",
                "NASA reports a total mission cost of $3.9 billion.",
                "The amount covers the complete mission.",
                "The mission operated for nearly two decades.",
                "The source amount is already in US dollars.",
                "NASA Science — Cassini quick facts",
                "https://science.nasa.gov/mission/cassini/quick-facts/",
                new ProjectImageView(
                        "/api/assets/budget/projects/Q165585/sha1",
                        "Cassini spacecraft",
                        "NASA",
                        "NASA spacecraft artwork",
                        "Public domain",
                        "https://commons.wikimedia.org/wiki/Commons:Copyright_tags",
                        "https://commons.wikimedia.org/wiki/File:Cassini_spacecraft_model.png"),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ProjectFactView("Launch", "1997", null, null)),
                List.of(),
                LocalDate.of(2026, 9, 20));
    }

    @Test
    void returnsCategoryRankingAsJson() throws Exception {
        when(projectPageService.findCategoryHub("stadiums")).thenReturn(Optional.of(stadiumHub()));

        mockMvc.perform(get("/api/project-pages/hubs/most-expensive/stadiums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CATEGORY"))
                .andExpect(jsonPath("$.path").value("/projects/most-expensive/stadiums"))
                .andExpect(jsonPath("$.projects[0].rank").value(1))
                .andExpect(jsonPath("$.projects[0].costUsd").value("$1.5 billion"));
    }

    @Test
    void unknownCategoryRankingIsNotFound() throws Exception {
        when(projectPageService.findCategoryHub("castles")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/project-pages/hubs/most-expensive/castles"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownCountryRankingIsNotFound() throws Exception {
        when(projectPageService.findCountryHub("atlantis")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/project-pages/hubs/countries/atlantis"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sitemapListsRankingPages() throws Exception {
        when(projectPageService.findHubPaths()).thenReturn(List.of(
                "/projects/most-expensive",
                "/projects/most-expensive/dams",
                "/projects/countries/japan"));
        when(projectPageService.findSitemapRows()).thenReturn(List.of());

        mockMvc.perform(get("/api/project-pages/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<loc>https://clueterra.com/projects/most-expensive/dams</loc>")))
                .andExpect(content().string(containsString(
                        "<loc>https://clueterra.com/projects/countries/japan</loc>")));
    }

    private ProjectHubPage stadiumHub() {
        return new ProjectHubPage(
                ProjectHubKind.CATEGORY,
                "/projects/most-expensive/stadiums",
                "STADIUM",
                null,
                null,
                1,
                List.of(new ProjectHubEntry(
                        1,
                        "wembley-stadium",
                        "Wembley Stadium",
                        "United Kingdom",
                        "Stadium",
                        2007,
                        "$1.5 billion",
                        "£1.2 billion",
                        "Reported project cost",
                        null,
                        false,
                        null,
                        ProjectRankingScope.WHOLE,
                        "/api/assets/budget/projects/Q1/sha1",
                        "Stadium photo")),
                List.of(),
                List.of());
    }
}
