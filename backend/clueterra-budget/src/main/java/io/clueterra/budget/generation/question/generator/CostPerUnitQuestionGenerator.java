package io.clueterra.budget.generation.question.generator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.clueterra.budget.generation.question.model.BudgetCostPerUnitMetric;
import io.clueterra.budget.generation.question.model.BudgetCostPerUnitSelection;
import io.clueterra.budget.generation.question.model.BudgetGeneratedQuestionDraft;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetQuestionMetricFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectMetricType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CostPerUnitQuestionGenerator implements BudgetQuestionVariantGenerator {

    private static final Map<MegaprojectCategory, List<ProjectMetricType>> METRIC_PRIORITY =
            metricPriority();

    private final BudgetQuestionPayloadFactory payloadFactory;

    @Override
    public BudgetQuestionType type() {
        return BudgetQuestionType.COST_PER_UNIT;
    }

    @Override
    public List<BudgetGeneratedQuestionDraft> generate(
            List<BudgetQuestionGenerationContext> contexts) {
        return contexts.stream()
                .map(this::selectMetric)
                .flatMap(Optional::stream)
                .map(selection -> create(selection.context(), selection.metric()))
                .toList();
    }

    private BudgetGeneratedQuestionDraft create(
            BudgetQuestionGenerationContext context,
            BudgetCostPerUnitMetric metric) {
        BigDecimal answer = context.source().answerCostUsd()
                .divide(metric.denominator(), 6, RoundingMode.HALF_UP);
        return new BudgetGeneratedQuestionDraft(
                context.source().questionId(),
                null,
                type(),
                BudgetGeneratedQuestionDraft.unaryKey(
                        type(), context.source().questionId()),
                answer,
                metric.answerUnit(),
                null,
                payloadFactory.metricPrompt(
                        context, metric.metric(), metric.displayValue(), metric.displayUnit()),
                payloadFactory.reveal(context),
                BudgetQuestionComputation.promote(context.source().difficulty(), 1));
    }

    private Optional<BudgetCostPerUnitSelection> selectMetric(BudgetQuestionGenerationContext context) {
        return METRIC_PRIORITY.getOrDefault(context.source().category(), List.of()).stream()
                .map(context::metric)
                .flatMap(Optional::stream)
                .findFirst()
                .map(metric -> new BudgetCostPerUnitSelection(context, normalize(metric)));
    }

    private BudgetCostPerUnitMetric normalize(BudgetQuestionMetricFact metric) {
        return switch (metric.type()) {
            case LENGTH -> new BudgetCostPerUnitMetric(
                    metric,
                    metric.normalizedValue().movePointLeft(3),
                    metric.normalizedValue().movePointLeft(3),
                    "KM",
                    "USD_PER_KM");
            case HEIGHT -> new BudgetCostPerUnitMetric(
                    metric,
                    metric.normalizedValue(),
                    metric.normalizedValue(),
                    "M",
                    "USD_PER_METER");
            case FLOOR_AREA -> new BudgetCostPerUnitMetric(
                    metric,
                    metric.normalizedValue(),
                    metric.normalizedValue(),
                    "M2",
                    "USD_PER_SQUARE_METER");
            case CAPACITY -> new BudgetCostPerUnitMetric(
                    metric,
                    metric.normalizedValue(),
                    metric.normalizedValue(),
                    "SEAT",
                    "USD_PER_SEAT");
            case POWER -> new BudgetCostPerUnitMetric(
                    metric,
                    metric.normalizedValue(),
                    metric.normalizedValue(),
                    "MW",
                    "USD_PER_MW");
            case ANNUAL_PASSENGERS -> new BudgetCostPerUnitMetric(
                    metric,
                    metric.normalizedValue(),
                    metric.normalizedValue(),
                    "PASSENGERS_PER_YEAR",
                    "USD_PER_ANNUAL_PASSENGER");
            case ANNUAL_CARGO_TONNES -> new BudgetCostPerUnitMetric(
                    metric,
                    metric.normalizedValue(),
                    metric.normalizedValue(),
                    "TONNES_PER_YEAR",
                    "USD_PER_ANNUAL_CARGO_TONNE");
            case CONTAINER_CAPACITY -> new BudgetCostPerUnitMetric(
                    metric,
                    metric.normalizedValue(),
                    metric.normalizedValue(),
                    "TEU_PER_YEAR",
                    "USD_PER_ANNUAL_TEU");
            case STORAGE_VOLUME -> new BudgetCostPerUnitMetric(
                    metric,
                    metric.normalizedValue().movePointLeft(6),
                    metric.normalizedValue().movePointLeft(6),
                    "MILLION_M3",
                    "USD_PER_MILLION_M3");
            case MASS -> new BudgetCostPerUnitMetric(
                    metric,
                    metric.normalizedValue(),
                    metric.normalizedValue(),
                    "KG",
                    "USD_PER_KG");
            case MAIN_SPAN -> throw new IllegalArgumentException(
                    "Main span is not a supported cost-per-unit denominator");
        };
    }

    private static Map<MegaprojectCategory, List<ProjectMetricType>> metricPriority() {
        EnumMap<MegaprojectCategory, List<ProjectMetricType>> priorities =
                new EnumMap<>(MegaprojectCategory.class);
        priorities.put(MegaprojectCategory.BRIDGE, List.of(ProjectMetricType.LENGTH));
        priorities.put(MegaprojectCategory.METRO_RAIL, List.of(ProjectMetricType.LENGTH));
        priorities.put(MegaprojectCategory.STADIUM, List.of(ProjectMetricType.CAPACITY));
        priorities.put(
                MegaprojectCategory.SKYSCRAPER,
                List.of(ProjectMetricType.FLOOR_AREA, ProjectMetricType.HEIGHT));
        priorities.put(
                MegaprojectCategory.AIRPORT, List.of(ProjectMetricType.ANNUAL_PASSENGERS));
        priorities.put(MegaprojectCategory.TUNNEL, List.of(ProjectMetricType.LENGTH));
        priorities.put(
                MegaprojectCategory.DAM,
                List.of(ProjectMetricType.POWER, ProjectMetricType.STORAGE_VOLUME));
        priorities.put(MegaprojectCategory.ENERGY, List.of(ProjectMetricType.POWER));
        priorities.put(MegaprojectCategory.SPACE, List.of(ProjectMetricType.MASS));
        priorities.put(MegaprojectCategory.ROAD_HIGHWAY, List.of(ProjectMetricType.LENGTH));
        priorities.put(
                MegaprojectCategory.PORT,
                List.of(ProjectMetricType.CONTAINER_CAPACITY, ProjectMetricType.ANNUAL_CARGO_TONNES));
        return Map.copyOf(priorities);
    }
}
