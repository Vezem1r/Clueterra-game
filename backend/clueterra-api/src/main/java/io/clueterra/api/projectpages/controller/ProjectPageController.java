package io.clueterra.api.projectpages.controller;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.config.OAuthProperties;
import io.clueterra.api.exception.ResourceNotFoundException;
import io.clueterra.api.projectpages.repository.ProjectDirectoryFilter;
import io.clueterra.api.projectpages.service.ProjectDetailPageResponse;
import io.clueterra.api.projectpages.service.ProjectDirectoryPage;
import io.clueterra.api.projectpages.service.ProjectHubPage;
import io.clueterra.api.projectpages.service.ProjectPageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/project-pages")
@RequiredArgsConstructor
@Validated
@Tag(name = "Project pages", description = "Public BudgetGuessr project data and sitemap")
public class ProjectPageController {

    private static final int DIRECTORY_PAGE_SIZE = 24;
    private static final String PROJECT_CATEGORY_PATTERN =
            "^(?:BRIDGE|METRO_RAIL|STADIUM|SKYSCRAPER|AIRPORT|TUNNEL|DAM|ENERGY|SPACE|ROAD_HIGHWAY|PORT)$";
    private static final List<String> STATIC_PUBLIC_PATHS = List.of(
            "/",
            "/games",
            "/games/rivers",
            "/games/rivers/how-to",
            "/games/country-silhouettes",
            "/games/country-silhouettes/how-to",
            "/games/country-flags",
            "/games/country-flags/how-to",
            "/games/countries-quiz",
            "/games/countries-quiz/how-to",
            "/about",
            "/how-to",
            "/budget",
            "/budget/how-to",
            "/terms",
            "/privacy",
            "/data-sources",
            "/projects");
    private static final List<String> LOCALIZED_PREFIXES = List.of("/ru", "/cs");
    private static final List<String> LOCALIZED_PUBLIC_PATHS = List.of(
            "/games",
            "/games/rivers",
            "/games/rivers/how-to",
            "/games/country-silhouettes",
            "/games/country-silhouettes/how-to",
            "/games/country-flags",
            "/games/country-flags/how-to",
            "/games/countries-quiz",
            "/games/countries-quiz/how-to");

    private final ProjectPageService projectPageService;
    private final OAuthProperties oauthProperties;

    @GetMapping(value = "/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get one page of published projects for the React project directory")
    public ResponseEntity<ProjectDirectoryPage> directory(
            @RequestParam(defaultValue = "1")
            @Min(1)
            int page,
            @RequestParam(name = "q", required = false)
            @Size(max = 100)
            String search,
            @RequestParam(required = false)
            @Pattern(regexp = PROJECT_CATEGORY_PATTERN)
            String category,
            @RequestParam(required = false)
            @DecimalMin("0")
            BigDecimal minCost,
            @RequestParam(required = false)
            @DecimalMin("0")
            BigDecimal maxCost,
            @RequestParam(required = false)
            @Min(1)
            @Max(9999)
            Integer yearFrom,
            @RequestParam(required = false)
            @Min(1)
            @Max(9999)
            Integer yearTo) {
        ProjectDirectoryFilter filter = new ProjectDirectoryFilter(
                search,
                category,
                minCost,
                maxCost,
                yearFrom,
                yearTo);
        ProjectDirectoryPage directory = projectPageService.findPublishedCards(page, DIRECTORY_PAGE_SIZE, filter)
                .orElseThrow(() -> new ResourceNotFoundException("Project directory page not found"));
        return ResponseEntity.ok()
                .cacheControl(publicCacheControl())
                .body(directory);
    }

    @GetMapping(value = "/data/{slug:[a-z0-9]+(?:-[a-z0-9]+)*}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a published project for the React project page")
    public ResponseEntity<ProjectDetailPageResponse> detail(
            @PathVariable
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
            String slug) {
        ProjectDetailPageResponse projectPage = projectPageService.findPublishedProject(slug)
                .map(project -> new ProjectDetailPageResponse(
                        project,
                        projectPageService.findRelated(project, 3)))
                .orElseThrow(() -> new ResourceNotFoundException("Published project page not found"));
        return ResponseEntity.ok()
                .cacheControl(publicCacheControl())
                .body(projectPage);
    }

    @GetMapping(value = "/hubs/most-expensive", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the most expensive published projects across all categories")
    public ResponseEntity<ProjectHubPage> allHub() {
        return ResponseEntity.ok()
                .cacheControl(publicCacheControl())
                .body(projectPageService.findAllHub());
    }

    @GetMapping(value = "/hubs/most-expensive/{category:[a-z0-9]+(?:-[a-z0-9]+)*}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the most expensive published projects in one category")
    public ResponseEntity<ProjectHubPage> categoryHub(
            @PathVariable
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
            String category) {
        ProjectHubPage hub = projectPageService.findCategoryHub(category)
                .orElseThrow(() -> new ResourceNotFoundException("Project category ranking not found"));
        return ResponseEntity.ok()
                .cacheControl(publicCacheControl())
                .body(hub);
    }

    @GetMapping(value = "/hubs/countries/{country:[a-z0-9]+(?:-[a-z0-9]+)*}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the most expensive published projects in one country")
    public ResponseEntity<ProjectHubPage> countryHub(
            @PathVariable
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
            String country) {
        ProjectHubPage hub = projectPageService.findCountryHub(country)
                .orElseThrow(() -> new ResourceNotFoundException("Project country ranking not found"));
        return ResponseEntity.ok()
                .cacheControl(publicCacheControl())
                .body(hub);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Build a sitemap containing static and published project pages")
    public ResponseEntity<String> sitemap() {
        String siteOrigin = siteOrigin();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        STATIC_PUBLIC_PATHS.forEach(path -> appendSitemapUrl(xml, siteOrigin + path, null));
        LOCALIZED_PREFIXES.forEach(prefix -> LOCALIZED_PUBLIC_PATHS.forEach(path -> appendSitemapUrl(xml, siteOrigin + prefix + path, null)));
        projectPageService.findHubPaths().forEach(path -> appendSitemapUrl(xml, siteOrigin + path, null));
        projectPageService.findSitemapRows().forEach(row -> appendSitemapUrl(xml, siteOrigin + "/projects/" + row.slug(), LocalDate.ofInstant(row.updatedAt(), ZoneOffset.UTC)));
        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .cacheControl(publicCacheControl())
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.toString());
    }

    private CacheControl publicCacheControl() {
        return CacheControl.maxAge(Duration.ofMinutes(5))
                .cachePublic()
                .staleWhileRevalidate(Duration.ofMinutes(10));
    }

    private String siteOrigin() {
        String value = oauthProperties.getFrontendRedirectUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void appendSitemapUrl(StringBuilder xml, String location, LocalDate lastModified) {
        xml.append("  <url><loc>").append(location).append("</loc>");
        if (lastModified != null) {
            xml.append("<lastmod>").append(lastModified).append("</lastmod>");
        }
        xml.append("</url>\n");
    }
}
