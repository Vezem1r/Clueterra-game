package io.clueterra.api.projectpages.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import io.clueterra.api.common.utils.SlugUtils;
import io.clueterra.api.projectpages.repository.ProjectHubCardRow;
import io.clueterra.api.projectpages.repository.ProjectHubCategoryRow;
import io.clueterra.api.projectpages.repository.ProjectPageCardRow;
import io.clueterra.api.projectpages.repository.ProjectPageCostRow;
import io.clueterra.api.projectpages.repository.ProjectPageDetailRow;
import io.clueterra.api.projectpages.repository.ProjectPageFactRow;
import io.clueterra.api.projectpages.repository.ProjectPageJdbcRepository;
import io.clueterra.api.projectpages.repository.ProjectPageMetricRow;
import io.clueterra.api.projectpages.repository.ProjectPageMilestoneRow;
import io.clueterra.api.projectpages.repository.ProjectDirectoryFilter;
import io.clueterra.api.projectpages.repository.ProjectSitemapRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectPageService {

    private static final BigDecimal ONE_BILLION = new BigDecimal("1000000000");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
    private static final String HUB_ROOT_PATH = "/projects/most-expensive";
    private static final String COUNTRY_HUB_ROOT_PATH = "/projects/countries";
    private static final int ALL_HUB_LIMIT = 50;
    private static final int CATEGORY_HUB_LIMIT = 25;
    private static final int COUNTRY_HUB_LIMIT = 30;
    private static final int COUNTRY_HUB_MINIMUM_PROJECTS = 8;
    private static final String CURRENT_ESTIMATE = "CURRENT_ESTIMATE";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);

    private final ProjectPageJdbcRepository repository;

    public Optional<ProjectDirectoryPage> findPublishedCards(
            int pageNumber,
            int pageSize,
            ProjectDirectoryFilter filter) {
        if (pageNumber < 1 || pageSize < 1) {
            return Optional.empty();
        }
        validateFilter(filter);
        long totalProjects = repository.countPublishedCards(filter);
        int totalPages = Math.max(1, (int) Math.ceilDiv(totalProjects, pageSize));
        if (pageNumber > totalPages) {
            return Optional.empty();
        }
        int offset = Math.multiplyExact(pageNumber - 1, pageSize);
        List<ProjectCardView> projects = repository.findPublishedCards(filter, pageSize, offset).stream()
                .map(this::toCard)
                .toList();
        return Optional.of(new ProjectDirectoryPage(
                projects,
                pageNumber,
                pageSize,
                totalProjects,
                totalPages,
                findCountryHubs()));
    }

    private void validateFilter(ProjectDirectoryFilter filter) {
        if (filter.minimumCostUsd() != null
                && filter.maximumCostUsd() != null
                && filter.minimumCostUsd().compareTo(filter.maximumCostUsd()) > 0) {
            throw new IllegalArgumentException("Minimum project cost cannot exceed maximum project cost");
        }
        if (filter.minimumYear() != null
                && filter.maximumYear() != null
                && filter.minimumYear() > filter.maximumYear()) {
            throw new IllegalArgumentException("Minimum project year cannot exceed maximum project year");
        }
    }

    public Optional<ProjectDetailView> findPublishedProject(String slug) {
        return repository.findPublishedBySlug(slug).map(this::toDetail);
    }

    public Optional<ProjectDetailView> findProjectPreview(String wikidataId) {
        return repository.findByWikidataId(wikidataId).map(this::toDetail);
    }

    public List<ProjectCardView> findRelated(ProjectDetailView project, int limit) {
        return repository.findRelatedCards(project.wikidataId(), limit).stream()
                .map(this::toCard)
                .toList();
    }

    public List<ProjectSitemapRow> findSitemapRows() {
        return repository.findSitemapRows();
    }

    public List<ProjectHubLink> findCategoryHubs() {
        Map<String, Long> counts = repository.countHubCategories().stream()
                .collect(Collectors.toMap(ProjectHubCategoryRow::category, ProjectHubCategoryRow::projectCount));
        return Arrays.stream(ProjectHubCategory.values())
                .filter(category -> counts.getOrDefault(category.name(), 0L) > 0)
                .map(category -> new ProjectHubLink(
                        categoryHubPath(category),
                        ProjectHubKind.CATEGORY,
                        category.name(),
                        null,
                        null,
                        counts.get(category.name())))
                .toList();
    }

    public List<ProjectHubLink> findCountryHubs() {
        return repository.findHubCountries(COUNTRY_HUB_MINIMUM_PROJECTS).stream()
                .map(row -> new ProjectHubLink(
                        COUNTRY_HUB_ROOT_PATH + "/" + SlugUtils.slugify(row.countryName()),
                        ProjectHubKind.COUNTRY,
                        null,
                        row.countryCode(),
                        row.countryName(),
                        row.projectCount()))
                .toList();
    }

    public List<String> findHubPaths() {
        return Stream.of(
                        List.of(HUB_ROOT_PATH),
                        findCategoryHubs().stream().map(ProjectHubLink::path).toList(),
                        findCountryHubs().stream().map(ProjectHubLink::path).toList())
                .flatMap(List::stream)
                .toList();
    }

    public ProjectHubPage findAllHub() {
        List<ProjectHubLink> categoryHubs = findCategoryHubs();
        long totalProjects = categoryHubs.stream().mapToLong(ProjectHubLink::projectCount).sum();
        return hubPage(
                ProjectHubKind.ALL,
                HUB_ROOT_PATH,
                null,
                null,
                totalProjects,
                repository.findHubCards(null, null, ALL_HUB_LIMIT),
                categoryHubs,
                findCountryHubs());
    }

    public Optional<ProjectHubPage> findCategoryHub(String slug) {
        Optional<ProjectHubCategory> category = ProjectHubCategory.fromSlug(slug);
        if (category.isEmpty()) {
            return Optional.empty();
        }
        List<ProjectHubLink> categoryHubs = findCategoryHubs();
        return categoryHubs.stream()
                .filter(link -> category.get().name().equals(link.category()))
                .findFirst()
                .map(link -> hubPage(
                        ProjectHubKind.CATEGORY,
                        link.path(),
                        link.category(),
                        null,
                        link.projectCount(),
                        repository.findHubCards(link.category(), null, CATEGORY_HUB_LIMIT),
                        categoryHubs,
                        findCountryHubs()));
    }

    public Optional<ProjectHubPage> findCountryHub(String slug) {
        String path = COUNTRY_HUB_ROOT_PATH + "/" + slug;
        List<ProjectHubLink> countryHubs = findCountryHubs();
        return countryHubs.stream()
                .filter(link -> link.path().equals(path))
                .findFirst()
                .map(link -> hubPage(
                        ProjectHubKind.COUNTRY,
                        link.path(),
                        null,
                        link,
                        link.projectCount(),
                        repository.findHubCards(null, link.countryCode(), COUNTRY_HUB_LIMIT),
                        findCategoryHubs(),
                        countryHubs));
    }

    private ProjectHubPage hubPage(
            ProjectHubKind kind,
            String path,
            String category,
            ProjectHubLink country,
            long totalProjects,
            List<ProjectHubCardRow> rows,
            List<ProjectHubLink> categoryHubs,
            List<ProjectHubLink> countryHubs) {
        List<ProjectHubEntry> projects = IntStream.range(0, rows.size())
                .mapToObj(index -> toHubEntry(index + 1, rows.get(index)))
                .toList();
        return new ProjectHubPage(
                kind,
                path,
                category,
                country == null ? null : country.countryCode(),
                country == null ? null : country.countryName(),
                totalProjects,
                projects,
                categoryHubs,
                countryHubs);
    }

    private ProjectHubEntry toHubEntry(int rank, ProjectHubCardRow row) {
        return new ProjectHubEntry(
                rank,
                row.slug(),
                row.name(),
                row.countryName(),
                categoryLabel(row.category()),
                row.projectYear(),
                formatMoney(row.answerCostUsd(), "USD", 2),
                formatMoney(row.sourceAmount(), row.sourceCurrency(), 4),
                costTypeLabel(row.costKind()),
                row.scopeLabel(),
                CURRENT_ESTIMATE.equals(row.costKind()),
                lifecycleLabel(row.lifecycleStatus()),
                Optional.ofNullable(row.rankingScope())
                        .map(ProjectRankingScope::valueOf)
                        .orElse(ProjectRankingScope.WHOLE),
                imageUrl(row.wikidataId(), row.imageSourceSha1()),
                row.imageAltText());
    }

    private String lifecycleLabel(String lifecycleStatus) {
        if (lifecycleStatus == null) {
            return null;
        }
        return switch (lifecycleStatus) {
            case "UNDER_CONSTRUCTION" -> "Under construction";
            case "ON_HOLD" -> "On hold";
            case "PLANNED" -> "Planned";
            case "CANCELLED" -> "Cancelled";
            case "ABANDONED" -> "Abandoned";
            default -> null;
        };
    }

    private String countryHubPath(String countryCode) {
        return findCountryHubs().stream()
                .filter(link -> link.countryCode().equals(countryCode))
                .map(ProjectHubLink::path)
                .findFirst()
                .orElse(null);
    }

    private String categoryHubPath(ProjectHubCategory category) {
        return HUB_ROOT_PATH + "/" + category.slug();
    }

    private ProjectCardView toCard(ProjectPageCardRow row) {
        return new ProjectCardView(
                row.slug(),
                row.name(),
                row.countryName(),
                categoryLabel(row.category()),
                row.projectYear(),
                formatMoney(row.sourceAmount(), row.sourceCurrency(), 4),
                usdEquivalent(row.sourceCurrency(), row.answerCostUsd()),
                row.metaDescription(),
                imageUrl(row.wikidataId(), row.imageSourceSha1()),
                row.imageAltText(),
                LocalDate.ofInstant(row.updatedAt(), ZoneOffset.UTC));
    }

    private ProjectDetailView toDetail(ProjectPageDetailRow row) {
        List<ProjectFactView> facts = row.facts().stream()
                .map(this::toFact)
                .toList();
        return new ProjectDetailView(
                row.wikidataId(),
                row.slug(),
                row.seoTitle(),
                row.metaDescription(),
                row.keywords(),
                row.name(),
                row.costQuestion(),
                row.countryName(),
                categoryLabel(row.category()),
                ProjectHubCategory.fromCode(row.category()).map(this::categoryHubPath).orElse(null),
                countryHubPath(row.countryCode()),
                row.projectYear(),
                formatMoney(row.sourceAmount(), row.sourceCurrency(), 4),
                usdEquivalent(row.sourceCurrency(), row.answerCostUsd()),
                row.costYear(),
                costTypeLabel(row.costKind()),
                row.scopeLabel(),
                lifecycleLabel(row.lifecycleStatus()),
                row.intro(),
                row.answerDetail(),
                row.scopeText(),
                row.contextText(),
                row.methodologyText(),
                row.sourceName(),
                row.sourceUrl(),
                new ProjectImageView(
                        imageUrl(row.wikidataId(), row.imageSourceSha1()),
                        row.imageAltText(),
                        row.imageAuthorName(),
                        row.imageAttributionText(),
                        row.imageLicenseLabel(),
                        row.imageLicenseUrl(),
                        row.imageSourcePageUrl()),
                row.costs().stream()
                        .map(this::toCost)
                        .toList(),
                row.metrics().stream()
                        .map(this::toMetric)
                        .toList(),
                row.milestones().stream()
                        .map(this::toMilestone)
                        .toList(),
                facts,
                row.sources().stream()
                        .map(source -> new ProjectSourceView(
                                source.name(),
                                source.url(),
                                source.claim()))
                        .toList(),
                LocalDate.ofInstant(row.updatedAt(), ZoneOffset.UTC));
    }

    private ProjectFactView toFact(ProjectPageFactRow row) {
        return new ProjectFactView(
                row.label(),
                row.value(),
                row.sourceName(),
                row.sourceUrl());
    }

    private ProjectCostView toCost(ProjectPageCostRow row) {
        return new ProjectCostView(
                costTypeLabel(row.costKind()),
                formatMoney(row.sourceAmount(), row.sourceCurrency(), 4),
                usdEquivalent(row.sourceCurrency(), row.amountUsd()),
                row.costYear(),
                row.featured(),
                row.scopeLabel(),
                row.sourceName(),
                row.sourceUrl());
    }

    private ProjectMetricView toMetric(ProjectPageMetricRow row) {
        return new ProjectMetricView(
                metricLabel(row.metricType()),
                formatMetric(row.metricType(), row.value(), row.unit()),
                row.sourceName(),
                row.sourceUrl());
    }

    private ProjectMilestoneView toMilestone(ProjectPageMilestoneRow row) {
        String value = row.dateValue() != null
                ? DATE_FORMATTER.format(row.dateValue())
                : row.yearValue().toString();
        return new ProjectMilestoneView(
                milestoneLabel(row.milestoneType()),
                value,
                row.sourceName(),
                row.sourceUrl());
    }

    private String formatMoney(BigDecimal amount, String currency, int maximumScale) {
        String prefix = switch (currency) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "JPY" -> "¥";
            case "GBP" -> "£";
            default -> currency + " ";
        };
        if (amount.compareTo(ONE_BILLION) >= 0) {
            return prefix + compact(amount.divide(ONE_BILLION, maximumScale, RoundingMode.HALF_UP)) + " billion";
        }
        if (amount.compareTo(ONE_MILLION) >= 0) {
            return prefix + compact(amount.divide(ONE_MILLION, maximumScale, RoundingMode.HALF_UP)) + " million";
        }
        return prefix + grouped(amount);
    }

    private String usdEquivalent(String sourceCurrency, BigDecimal answerCostUsd) {
        return "USD".equals(sourceCurrency)
                ? null
                : "Approximately " + formatMoney(answerCostUsd, "USD", 2);
    }

    private String compact(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String grouped(BigDecimal value) {
        DecimalFormat formatter = new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(Locale.US));
        return formatter.format(value);
    }

    private String formatMetric(String type, BigDecimal value, String unit) {
        if ("POWER".equals(type) && "MW".equals(unit) && value.compareTo(BigDecimal.ONE) < 0) {
            return grouped(value.multiply(ONE_MILLION)) + " W";
        }
        if ("LENGTH".equals(type) && "M".equals(unit) && value.compareTo(new BigDecimal("10000")) >= 0) {
            return compact(value.divide(ONE_THOUSAND, 3, RoundingMode.HALF_UP)) + " km";
        }
        String displayUnit = switch (unit) {
            case "M" -> "m";
            case "M2" -> "m²";
            case "M3" -> "m³";
            case "KG" -> "kg";
            case "MW" -> "MW";
            case "SEAT" -> "seats";
            case "PASSENGERS_PER_YEAR" -> "passengers/year";
            default -> unit;
        };
        return grouped(value) + " " + displayUnit;
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "METRO_RAIL" -> "Railway";
            case "SPACE" -> "Space mission";
            case "ENERGY" -> "Energy";
            case "BRIDGE" -> "Bridge";
            case "SKYSCRAPER" -> "Skyscraper";
            case "STADIUM" -> "Stadium";
            case "AIRPORT" -> "Airport";
            case "TUNNEL" -> "Tunnel";
            case "DAM" -> "Dam";
            case "ROAD_HIGHWAY" -> "Road or highway";
            case "PORT" -> "Port";
            default -> category;
        };
    }

    private String costTypeLabel(String costKind) {
        if (costKind == null) {
            return "Reported project cost";
        }
        return switch (costKind) {
            case "FINAL_COST" -> "Final project cost";
            case "CURRENT_ESTIMATE" -> "Published project estimate";
            case "ORIGINAL_BUDGET" -> "Original budget";
            case "APPROVED_BUDGET" -> "Approved budget";
            case "REVISED_BUDGET" -> "Revised budget";
            case "EXPENDITURE_TO_DATE" -> "Spent to date";
            case "FINANCING_AMOUNT" -> "Financing amount";
            case "CONTRACT_VALUE" -> "Contract value";
            default -> "Reported project cost";
        };
    }

    private String milestoneLabel(String type) {
        return switch (type) {
            case "CONSTRUCTION_START" -> "Construction started";
            case "OPENING" -> "Opened or launched";
            case "ACTUAL_COMPLETION" -> "Completed";
            case "PLANNED_COMPLETION" -> "Planned completion";
            case "CURRENT_COMPLETION_ESTIMATE" -> "Current completion estimate";
            default -> type;
        };
    }

    private String metricLabel(String type) {
        return switch (type) {
            case "LENGTH" -> "Total length";
            case "MAIN_SPAN" -> "Main span";
            case "HEIGHT" -> "Height";
            case "FLOOR_AREA" -> "Floor area";
            case "CAPACITY" -> "Capacity";
            case "POWER" -> "Power";
            case "ANNUAL_PASSENGERS" -> "Annual passengers";
            case "ANNUAL_CARGO_TONNES" -> "Annual cargo tonnage";
            case "CONTAINER_CAPACITY" -> "Container capacity";
            case "STORAGE_VOLUME" -> "Storage volume";
            case "MASS" -> "Mass";
            default -> type;
        };
    }

    private String imageUrl(String wikidataId, String sourceSha1) {
        return "/api/assets/budget/projects/" + wikidataId + "/" + sourceSha1;
    }
}
