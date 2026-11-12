package io.clueterra.questions.clients.budgetprojects;

import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.HintSanitizer;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BudgetProjectHintLens implements HintLens {

    private static final String PREFIX = "BUDGET_PROJECT_";
    private static final String PROJECT_SUFFIX = ":PROJECT";

    private final DynamicDifficultyCalculator difficultyCalculator;
    private final BudgetProjectDifficultyCalculator projectDifficultyCalculator;
    private final HintSanitizer hintSanitizer;
    private final StatisticRepository statisticRepository;
    private final Map<Long, Map<String, CountryStatistic>> countryStatistics = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "budget-projects";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of(PREFIX);
    }

    @Override
    public Set<DataSource> claimedSources() {
        return Set.of(DataSource.BUDGET_PROJECTS);
    }

    @Override
    public boolean canApply(StatisticContext context) {
        CountryStatistic statistic = context.statistic();
        return statistic.getSource() == DataSource.BUDGET_PROJECTS
                && statistic.getIndicatorCode() != null
                && statistic.getIndicatorCode().startsWith(PREFIX)
                && statistic.getIndicatorCode().endsWith(PROJECT_SUFFIX)
                && StringUtils.hasText(statistic.getValueText())
                && StringUtils.hasText(statistic.getImageUrl());
    }

    @Override
    public List<CandidateHint> generate(StatisticContext context) {
        if (!canApply(context)) {
            return List.of();
        }
        CountryStatistic image = context.statistic();
        String projectId = image.getIndicatorCode().substring(
                PREFIX.length(), image.getIndicatorCode().length() - PROJECT_SUFFIX.length());
        Map<String, CountryStatistic> facts = countryStatistics.computeIfAbsent(
                image.getCountryId(), this::loadCountryStatistics);
        BudgetProjectHintFacts project = new BudgetProjectHintFacts(
                image.getValueText(),
                text(facts, BudgetProjectIndicator.CATEGORY, projectId),
                text(facts, BudgetProjectIndicator.LIFECYCLE, projectId),
                openingYear(facts, projectId),
                value(facts, BudgetProjectIndicator.COST_USD, projectId),
                text(facts, BudgetProjectIndicator.COST_USD, projectId),
                metricType(facts, projectId),
                metricValue(facts, projectId));

        return List.of(CandidateHint.builder()
                .text(BudgetProjectHintText.format(project,
                        image.getValueText().equals(hintSanitizer.sanitize(
                                image.getValueText(), context.country()))))
                .type(HintType.IMAGE_FACT)
                .category(Category.INFRASTRUCTURE)
                .source(DataSource.BUDGET_PROJECTS)
                .sourceIndicator(image.getIndicatorCode())
                .sourceYear(project.openingYear())
                .estimatedDifficulty(projectDifficultyCalculator.calculate(
                        image.getIndicatorCode(), context.country().getDifficultyModifier(),
                        text(facts, BudgetProjectIndicator.DIFFICULTY, projectId), project))
                .estimatedQuality(difficultyCalculator.computeQuality(
                        image.getIndicatorCode(), 1, BigDecimal.valueOf(0.89)))
                .ambiguityCount(1)
                .lensName(name())
                .imageUrl(image.getImageUrl())
                .originalImageUrl(image.getOriginalImageUrl())
                .imageSourcePageUrl(image.getImageSourcePageUrl())
                .imageAuthorName(image.getImageAuthorName())
                .imageAttributionText(image.getImageAttributionText())
                .imageLicenseLabel(image.getImageLicenseLabel())
                .imageLicenseUrl(image.getImageLicenseUrl())
                .build());
    }

    @Override
    public void invalidateCache() {
        countryStatistics.clear();
    }

    private Map<String, CountryStatistic> loadCountryStatistics(Long countryId) {
        return statisticRepository.findCurrentByCountryIdAndSource(countryId, DataSource.BUDGET_PROJECTS)
                .stream()
                .collect(Collectors.toMap(CountryStatistic::getIndicatorCode, statistic -> statistic,
                        (first, ignored) -> first));
    }

    private String text(Map<String, CountryStatistic> facts, BudgetProjectIndicator indicator, String projectId) {
        CountryStatistic statistic = facts.get(indicator.code(projectId));
        return statistic == null ? null : statistic.getValueText();
    }

    private BigDecimal value(Map<String, CountryStatistic> facts,
                             BudgetProjectIndicator indicator, String projectId) {
        CountryStatistic statistic = facts.get(indicator.code(projectId));
        return statistic == null ? null : statistic.getValue();
    }

    private Integer openingYear(Map<String, CountryStatistic> facts, String projectId) {
        BigDecimal value = value(facts, BudgetProjectIndicator.OPENING_YEAR, projectId);
        return value == null ? null : value.intValue();
    }

    private BudgetProjectIndicator metric(Map<String, CountryStatistic> facts, String projectId) {
        return Arrays.stream(BudgetProjectIndicator.values())
                .filter(indicator -> indicator.metricType() != null)
                .filter(indicator -> value(facts, indicator, projectId) != null)
                .findFirst()
                .orElse(null);
    }

    private String metricType(Map<String, CountryStatistic> facts, String projectId) {
        BudgetProjectIndicator indicator = metric(facts, projectId);
        return indicator == null ? null : indicator.metricType();
    }

    private BigDecimal metricValue(Map<String, CountryStatistic> facts, String projectId) {
        BudgetProjectIndicator indicator = metric(facts, projectId);
        return indicator == null ? null : value(facts, indicator, projectId);
    }
}
