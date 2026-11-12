package io.clueterra.questions.clients.budgetprojects;

import io.clueterra.questions.clients.common.CountryReference;
import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.storage.ImageStorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetProjectClient implements DataSourceClient {

    private static final BigDecimal CONFIDENCE = BigDecimal.valueOf(0.90);
    private static final BigDecimal WEIGHT = BigDecimal.valueOf(0.88);

    private final BudgetProjectRepository projectRepository;
    private final SourcesProperties sourcesProperties;
    private final CountryJdbcRepository countryRepository;
    private final StatisticRepository statisticRepository;
    private final Optional<ImageStorageService> imageStorageService;

    private SourceProperties properties;

    @PostConstruct
    public void init() {
        properties = sourcesProperties.getSource(getSource());
    }

    @Override
    public DataSource getSource() {
        return DataSource.BUDGET_PROJECTS;
    }

    @Override
    public String getBaseUrl() {
        return "cl_budget_schema";
    }

    @Override
    public boolean isAvailable() {
        return properties != null && properties.isEnabled() && projectRepository.isAvailable();
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        requireAvailable();
        List<BudgetProjectSnapshot> projects = projectRepository.findProjects(null);
        Map<String, List<BudgetProjectSnapshot>> byCountry = projects.stream()
                .collect(Collectors.groupingBy(BudgetProjectSnapshot::countryCode,
                        LinkedHashMap::new, Collectors.toList()));
        Set<String> countries = new TreeSet<>(byCountry.keySet());
        statisticRepository.findCurrentBySource(getSource()).stream()
                .map(CountryStatistic::getCountryId)
                .distinct()
                .map(countryRepository::findById)
                .flatMap(Optional::stream)
                .map(Country::getIsoAlpha2)
                .forEach(countries::add);

        List<RawCountryData> result = new ArrayList<>();
        FetchProgressListener listener = FetchProgressListener.safe(progressListener);
        for (String countryCode : countries) {
            result.add(toCountry(countryCode, byCountry.getOrDefault(countryCode, List.of())));
            listener.onProgress(result.size(), countries.size(), countryCode);
        }
        log.info("[BUDGET_PROJECTS] Read {} projects and {} country slices from Budget schema",
                projects.size(), result.size());
        return result;
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (countryCode == null || !countryCode.matches("(?i)[A-Z]{2}")) {
            return Optional.empty();
        }
        requireAvailable();
        String normalized = countryCode.toUpperCase(Locale.ROOT);
        List<BudgetProjectSnapshot> projects = projectRepository.findProjects(normalized);
        if (projects.isEmpty() && countryRepository.findByIsoAlpha2(normalized).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toCountry(normalized, projects));
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new IllegalStateException("Budget project source or cl_budget_schema is unavailable");
        }
    }

    private RawCountryData toCountry(String countryCode, List<BudgetProjectSnapshot> projects) {
        String countryName = projects.stream()
                .map(BudgetProjectSnapshot::countryName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElseGet(() -> Optional.ofNullable(CountryReference.getCountryName(countryCode))
                        .orElse(countryCode));
        List<RawStatistic> statistics = projects.stream()
                .filter(BudgetProjectSnapshot::eligible)
                .flatMap(project -> toStatistics(project).stream())
                .toList();
        return RawCountryData.builder()
                .isoAlpha2(countryCode)
                .nameEn(countryName)
                .source(getSource())
                .statistics(statistics)
                .build();
    }

    private List<RawStatistic> toStatistics(BudgetProjectSnapshot project) {
        String imageUrl = imageStorageService
                .map(storage -> storage.copyBudgetProjectImage(
                        project.imageObjectKey(), imageTargetKey(project)))
                .orElseGet(() -> Optional.ofNullable(project.originalImageUrl()))
                .orElseThrow(() -> new IllegalStateException(
                        "No accessible image for Budget project " + project.wikidataId()));

        List<RawStatistic> statistics = new ArrayList<>();
        statistics.add(new RawStatistic(
                BudgetProjectIndicator.PROJECT.code(project.wikidataId()),
                "Project image", Category.INFRASTRUCTURE, null, project.name(), null, null,
                CONFIDENCE, WEIGHT, imageUrl, project.originalImageUrl(),
                project.imageSourcePageUrl(), project.imageAuthorName(),
                project.imageAttributionText(), project.imageLicenseLabel(), project.imageLicenseUrl()));
        statistics.add(textStatistic(project, BudgetProjectIndicator.CATEGORY, project.category()));
        statistics.add(textStatistic(project, BudgetProjectIndicator.DIFFICULTY, project.difficulty()));
        statistics.add(textStatistic(project, BudgetProjectIndicator.LIFECYCLE, project.lifecycleStatus()));

        if (project.openingYear() != null) {
            statistics.add(numericStatistic(project, BudgetProjectIndicator.OPENING_YEAR,
                    BigDecimal.valueOf(project.openingYear()), null));
        }
        if (project.costUsd() != null) {
            statistics.add(numericStatistic(project, BudgetProjectIndicator.COST_USD,
                    project.costUsd(), project.costKind()));
        }
        BudgetProjectIndicator.forMetric(project.metricType(), project.metricUnit())
                .filter(indicator -> project.metricValue() != null)
                .ifPresent(indicator -> statistics.add(numericStatistic(
                        project, indicator, project.metricValue(), null)));
        return statistics;
    }

    private RawStatistic textStatistic(BudgetProjectSnapshot project, BudgetProjectIndicator indicator, String value) {
        return new RawStatistic(indicator.code(project.wikidataId()), indicator.name(),
                Category.INFRASTRUCTURE, null, value, null, null, CONFIDENCE, WEIGHT);
    }

    private RawStatistic numericStatistic(BudgetProjectSnapshot project, BudgetProjectIndicator indicator,
                                          BigDecimal value, String qualifier) {
        return new RawStatistic(indicator.code(project.wikidataId()), indicator.name(),
                Category.INFRASTRUCTURE, value, qualifier, indicator.unit(), null, CONFIDENCE, WEIGHT);
    }

    private String imageTargetKey(BudgetProjectSnapshot project) {
        String objectKey = project.imageObjectKey();
        int dot = objectKey.lastIndexOf('.');
        String extension = dot >= 0 && objectKey.substring(dot).matches("(?i)\\.(?:jpe?g|png|webp)")
                ? objectKey.substring(dot).toLowerCase(Locale.ROOT) : ".jpg";
        String imageVersion = objectKey + ":" + Optional.ofNullable(project.imageSourceSha1()).orElse("");
        String version = UUID.nameUUIDFromBytes(imageVersion.getBytes(StandardCharsets.UTF_8)).toString();
        return "budget-projects/" + project.wikidataId() + "/" + version + extension;
    }
}
