package io.clueterra.budget.generation.question.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.clueterra.budget.generation.question.generator.BudgetQuestionComputation;
import io.clueterra.budget.generation.question.model.BudgetGeneratedQuestionDraft;
import io.clueterra.budget.generation.question.model.BudgetQuestionCostFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetQuestionMetricFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionMilestoneFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectMetricType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BudgetQuestionExperienceEnricher {

    private static final BigDecimal COMPARABLE_MULTIPLIER = BigDecimal.valueOf(85);
    private static final BigDecimal RANGE_MULTIPLIER = BigDecimal.valueOf(60);
    private static final double MINIMUM_COMPARABLE_RATIO = 1.0D / 3.0D;
    private static final double MAXIMUM_COMPARABLE_RATIO = 3.0D;
    private static final Map<MegaprojectCategory, List<ProjectMetricType>> METRIC_PRIORITY =
            metricPriority();

    private final ObjectMapper objectMapper;

    public List<BudgetGeneratedQuestionDraft> enrich(
            List<BudgetGeneratedQuestionDraft> variants,
            List<BudgetQuestionGenerationContext> contexts) {
        Map<Long, BudgetQuestionGenerationContext> contextsByQuestion = contexts.stream()
                .collect(Collectors.toUnmodifiableMap(
                        context -> context.source().questionId(), Function.identity()));
        return variants.stream()
                .map(variant -> enrichVariant(variant, variants, contextsByQuestion))
                .toList();
    }

    private BudgetGeneratedQuestionDraft enrichVariant(
            BudgetGeneratedQuestionDraft variant,
            List<BudgetGeneratedQuestionDraft> variants,
            Map<Long, BudgetQuestionGenerationContext> contexts) {
        BudgetQuestionGenerationContext context = contexts.get(variant.sourceQuestionId());
        if (context == null) {
            throw new IllegalStateException(
                    "Missing generation context for Budget question " + variant.sourceQuestionId());
        }

        ObjectNode reveal = variant.revealPayload().deepCopy();
        reveal.set("hints", hints(variant, context, variants, contexts));
        ObjectNode explanation = explanation(variant, context);
        if (!explanation.isEmpty()) {
            reveal.set("explanation", explanation);
        } else {
            reveal.remove("explanation");
        }
        return new BudgetGeneratedQuestionDraft(
                variant.sourceQuestionId(),
                variant.comparisonQuestionId(),
                variant.questionType(),
                variant.generationKey(),
                variant.answerValue(),
                variant.answerUnit(),
                variant.answerChoice(),
                variant.promptPayload(),
                reveal,
                variant.difficulty());
    }

    private ArrayNode hints(
            BudgetGeneratedQuestionDraft variant,
            BudgetQuestionGenerationContext context,
            List<BudgetGeneratedQuestionDraft> variants,
            Map<Long, BudgetQuestionGenerationContext> contexts) {
        ArrayNode hints = objectMapper.createArrayNode();
        if (variant.questionType() == BudgetQuestionType.HIGHER_LOWER) {
            return hints;
        }

        comparable(variant, context, variants, contexts)
                .ifPresent(comparable -> hints.add(comparableHint(comparable, contexts)));
        hints.add(rangeHint(variant));
        return hints;
    }

    private Optional<BudgetGeneratedQuestionDraft> comparable(
            BudgetGeneratedQuestionDraft target,
            BudgetQuestionGenerationContext targetContext,
            List<BudgetGeneratedQuestionDraft> variants,
            Map<Long, BudgetQuestionGenerationContext> contexts) {
        return variants.stream()
                .filter(candidate -> candidate.questionType() == target.questionType())
                .filter(candidate -> candidate.sourceQuestionId() != target.sourceQuestionId())
                .filter(candidate -> candidate.answerValue() != null)
                .filter(candidate -> candidate.answerUnit().equals(target.answerUnit()))
                .filter(candidate -> contexts.containsKey(candidate.sourceQuestionId()))
                .filter(candidate -> contexts.get(candidate.sourceQuestionId()).source().category()
                        == targetContext.source().category())
                .filter(candidate -> comparableRatio(
                        target.answerValue(), candidate.answerValue()))
                .filter(candidate -> comparableMetric(
                        target, targetContext, candidate, contexts.get(candidate.sourceQuestionId()))
                        .isPresent())
                .min(Comparator.comparingDouble(candidate -> comparisonScore(
                        target,
                        targetContext,
                        candidate,
                        contexts.get(candidate.sourceQuestionId()))));
    }

    private ObjectNode comparableHint(
            BudgetGeneratedQuestionDraft comparable,
            Map<Long, BudgetQuestionGenerationContext> contexts) {
        BudgetQuestionGenerationContext context = contexts.get(comparable.sourceQuestionId());
        ObjectNode hint = objectMapper.createObjectNode();
        hint.put("type", "COMPARABLE_PROJECT");
        hint.put("multiplierPercent", COMPARABLE_MULTIPLIER);
        hint.put("projectName", context.source().name());
        hint.put("value", comparable.answerValue());
        hint.put("unit", comparable.answerUnit());
        comparableMetric(comparable, context, comparable, context).ifPresent(metric -> {
            hint.put("metricType", metric.type().name());
            hint.put("metricValue", metric.normalizedValue());
            hint.put("metricUnit", metric.normalizedUnit());
        });
        return hint;
    }

    private ObjectNode rangeHint(BudgetGeneratedQuestionDraft variant) {
        ObjectNode hint = objectMapper.createObjectNode();
        hint.put("type", "ANSWER_RANGE");
        hint.put("multiplierPercent", RANGE_MULTIPLIER);
        hint.put("unit", variant.answerUnit());
        BigDecimal answer = variant.answerValue();
        if (variant.questionType() == BudgetQuestionType.HOW_LATE) {
            hint.put("minimum", roundedLinearBound(answer, true, BigDecimal.valueOf(6)));
            hint.put("maximum", roundedLinearBound(answer, false, BigDecimal.valueOf(6)));
        } else if (variant.questionType() == BudgetQuestionType.BUDGET_OVERRUN) {
            hint.put("minimum", roundedLinearBound(answer, true, BigDecimal.TEN));
            hint.put("maximum", roundedLinearBound(answer, false, BigDecimal.TEN));
        } else {
            hint.put("minimum", niceFloor(answer.divide(BigDecimal.valueOf(2), 12, RoundingMode.HALF_UP)));
            hint.put("maximum", niceCeiling(answer.multiply(BigDecimal.valueOf(2))));
        }
        return hint;
    }

    private ObjectNode explanation(
            BudgetGeneratedQuestionDraft variant, BudgetQuestionGenerationContext context) {
        ObjectNode explanation = objectMapper.createObjectNode();

        switch (variant.questionType()) {
            case ORIGINAL_BUDGET -> context.cost(ProjectCostKind.ORIGINAL_BUDGET)
                    .ifPresent(cost -> addConversion(explanation, cost));
            case CURRENT_ESTIMATE -> costFor(context, ProjectCostKind.CURRENT_ESTIMATE)
                    .ifPresent(cost -> addConversion(explanation, cost));
            case SPENT_SO_FAR -> costFor(context, ProjectCostKind.EXPENDITURE_TO_DATE)
                    .ifPresent(cost -> addConversion(explanation, cost));
            case HOW_MUCH -> costFor(context, context.source().answerCostKind())
                    .ifPresent(cost -> addConversion(explanation, cost));
            case COST_PER_UNIT -> addCostPerUnitExplanation(explanation, variant, context);
            case BUDGET_OVERRUN -> addOverrunExplanation(explanation, variant, context);
            case HOW_LATE -> addDelayExplanation(explanation, context);
            case HIGHER_LOWER -> {
                // Comparison reveals already explain both projects independently.
            }
        }
        return explanation;
    }

    private void addConversion(ObjectNode explanation, BudgetQuestionCostFact cost) {
        if ("USD".equalsIgnoreCase(cost.sourceCurrency())) {
            return;
        }
        ObjectNode conversion = explanation.putObject("conversion");
        conversion.put("sourceAmountLow", cost.sourceAmountLow());
        conversion.put("sourceAmountHigh", cost.sourceAmountHigh());
        conversion.put("sourceCurrency", cost.sourceCurrency());
        conversion.put("amountUsdLow", cost.amountUsdLow());
        conversion.put("amountUsdHigh", cost.amountUsdHigh());
        put(conversion, "costYear", cost.costYear());
    }

    private void addCostPerUnitExplanation(
            ObjectNode explanation,
            BudgetGeneratedQuestionDraft variant,
            BudgetQuestionGenerationContext context) {
        if (variant.promptPayload().metric() == null) {
            return;
        }
        ObjectNode formula = explanation.putObject("formula");
        formula.put("operation", "DIVIDE");
        formula.put("numerator", context.source().answerCostUsd());
        formula.put("numeratorUnit", "USD");
        formula.put("denominator", variant.promptPayload().metric().value());
        formula.put("denominatorUnit", variant.promptPayload().metric().unit());
        formula.put("result", variant.answerValue());
        formula.put("resultUnit", variant.answerUnit());
    }

    private void addOverrunExplanation(
            ObjectNode explanation,
            BudgetGeneratedQuestionDraft variant,
            BudgetQuestionGenerationContext context) {
        Optional<BudgetQuestionCostFact> original = context.cost(ProjectCostKind.ORIGINAL_BUDGET);
        Optional<BudgetQuestionCostFact> finalCost = costFor(
                context, context.source().answerCostKind());
        if (original.isEmpty() || finalCost.isEmpty()) {
            return;
        }
        ObjectNode formula = explanation.putObject("formula");
        formula.put("operation", "PERCENT_INCREASE");
        formula.put("original", original.orElseThrow().midpointUsd());
        formula.put("final", finalCost.orElseThrow().midpointUsd());
        formula.put("unit", "USD");
        formula.put("result", variant.answerValue());
        formula.put("resultUnit", variant.answerUnit());
    }

    private void addDelayExplanation(
            ObjectNode explanation, BudgetQuestionGenerationContext context) {
        BudgetQuestionComputation.delay(context).ifPresent(delay -> {
            ObjectNode formula = explanation.putObject("formula");
            formula.put("operation", "MONTHS_BETWEEN");
            formula.set("planned", milestone(delay.planned()));
            formula.set("actual", milestone(delay.actual()));
            formula.put("result", delay.months());
            formula.put("resultUnit", "MONTHS");
        });
    }

    private ObjectNode milestone(BudgetQuestionMilestoneFact milestone) {
        ObjectNode value = objectMapper.createObjectNode();
        put(value, "date", milestone.date());
        put(value, "year", milestone.year());
        return value;
    }

    private Optional<BudgetQuestionCostFact> costFor(
            BudgetQuestionGenerationContext context, ProjectCostKind kind) {
        return context.cost(kind).or(() -> context.costs().stream()
                .filter(cost -> cost.midpointUsd().compareTo(context.source().answerCostUsd()) == 0)
                .findFirst());
    }

    private Optional<BudgetQuestionMetricFact> comparableMetric(
            BudgetGeneratedQuestionDraft target,
            BudgetQuestionGenerationContext targetContext,
            BudgetGeneratedQuestionDraft candidate,
            BudgetQuestionGenerationContext candidateContext) {
        List<ProjectMetricType> priorities = target.promptPayload().metric() == null
                ? METRIC_PRIORITY.getOrDefault(targetContext.source().category(), List.of())
                : List.of(target.promptPayload().metric().type());
        return priorities.stream()
                .map(type -> metricPair(targetContext, candidateContext, type))
                .flatMap(Optional::stream)
                .filter(pair -> comparableRatio(
                        pair[0].normalizedValue(), pair[1].normalizedValue()))
                .map(pair -> pair[1])
                .findFirst();
    }

    private Optional<BudgetQuestionMetricFact[]> metricPair(
            BudgetQuestionGenerationContext target,
            BudgetQuestionGenerationContext candidate,
            ProjectMetricType type) {
        Optional<BudgetQuestionMetricFact> first = target.metric(type);
        Optional<BudgetQuestionMetricFact> second = candidate.metric(type);
        return first.isPresent() && second.isPresent()
                ? Optional.of(new BudgetQuestionMetricFact[] {first.orElseThrow(), second.orElseThrow()})
                : Optional.empty();
    }

    private double comparisonScore(
            BudgetGeneratedQuestionDraft target,
            BudgetQuestionGenerationContext targetContext,
            BudgetGeneratedQuestionDraft candidate,
            BudgetQuestionGenerationContext candidateContext) {
        double answerDistance = logarithmicDistance(target.answerValue(), candidate.answerValue());
        double metricDistance = METRIC_PRIORITY
                .getOrDefault(targetContext.source().category(), List.of()).stream()
                .map(type -> metricPair(targetContext, candidateContext, type))
                .flatMap(Optional::stream)
                .mapToDouble(pair -> logarithmicDistance(
                        pair[0].normalizedValue(), pair[1].normalizedValue()))
                .findFirst()
                .orElse(1.0D);
        return metricDistance + answerDistance * 0.35D;
    }

    private boolean comparableRatio(BigDecimal first, BigDecimal second) {
        double ratio = first.doubleValue() / second.doubleValue();
        return ratio >= MINIMUM_COMPARABLE_RATIO && ratio <= MAXIMUM_COMPARABLE_RATIO;
    }

    private double logarithmicDistance(BigDecimal first, BigDecimal second) {
        return Math.abs(Math.log10(first.doubleValue() / second.doubleValue()));
    }

    private BigDecimal roundedLinearBound(
            BigDecimal answer, boolean lower, BigDecimal step) {
        BigDecimal factor = lower ? BigDecimal.valueOf(0.5D) : BigDecimal.valueOf(1.5D);
        BigDecimal offset = lower ? BigDecimal.ZERO : step;
        BigDecimal raw = answer.multiply(factor).add(offset).max(BigDecimal.ZERO);
        RoundingMode rounding = lower ? RoundingMode.FLOOR : RoundingMode.CEILING;
        return raw.divide(step, 0, rounding).multiply(step);
    }

    private BigDecimal niceFloor(BigDecimal value) {
        return niceBound(value, RoundingMode.FLOOR);
    }

    private BigDecimal niceCeiling(BigDecimal value) {
        return niceBound(value, RoundingMode.CEILING);
    }

    private BigDecimal niceBound(BigDecimal value, RoundingMode roundingMode) {
        if (value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        int exponent = value.precision() - value.scale() - 1;
        BigDecimal magnitude = BigDecimal.TEN.pow(Math.max(0, exponent));
        if (exponent < 0) {
            magnitude = BigDecimal.ONE.movePointLeft(-exponent);
        }
        BigDecimal normalized = value.divide(magnitude, 12, RoundingMode.HALF_UP);
        BigDecimal selected = Stream.of(
                        BigDecimal.ONE,
                        BigDecimal.valueOf(2),
                        BigDecimal.valueOf(5),
                        BigDecimal.TEN)
                .filter(candidate -> roundingMode == RoundingMode.FLOOR
                        ? candidate.compareTo(normalized) <= 0
                        : candidate.compareTo(normalized) >= 0)
                .reduce((first, second) -> roundingMode == RoundingMode.FLOOR ? second : first)
                .orElse(roundingMode == RoundingMode.FLOOR ? BigDecimal.ONE : BigDecimal.TEN);
        return selected.multiply(magnitude).stripTrailingZeros();
    }

    private void put(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private void put(ObjectNode node, String field, Integer value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private void put(ObjectNode node, String field, LocalDate value) {
        if (value != null) {
            node.put(field, value.toString());
        }
    }

    private static Map<MegaprojectCategory, List<ProjectMetricType>> metricPriority() {
        EnumMap<MegaprojectCategory, List<ProjectMetricType>> priorities =
                new EnumMap<>(MegaprojectCategory.class);
        priorities.put(MegaprojectCategory.BRIDGE, List.of(ProjectMetricType.LENGTH));
        priorities.put(MegaprojectCategory.METRO_RAIL, List.of(ProjectMetricType.LENGTH));
        priorities.put(MegaprojectCategory.STADIUM, List.of(ProjectMetricType.CAPACITY));
        priorities.put(MegaprojectCategory.SKYSCRAPER,
                List.of(ProjectMetricType.FLOOR_AREA, ProjectMetricType.HEIGHT));
        priorities.put(MegaprojectCategory.AIRPORT,
                List.of(ProjectMetricType.ANNUAL_PASSENGERS));
        priorities.put(MegaprojectCategory.TUNNEL, List.of(ProjectMetricType.LENGTH));
        priorities.put(MegaprojectCategory.DAM,
                List.of(ProjectMetricType.POWER, ProjectMetricType.STORAGE_VOLUME));
        priorities.put(MegaprojectCategory.ENERGY, List.of(ProjectMetricType.POWER));
        priorities.put(MegaprojectCategory.SPACE, List.of(ProjectMetricType.MASS));
        priorities.put(MegaprojectCategory.ROAD_HIGHWAY, List.of(ProjectMetricType.LENGTH));
        priorities.put(MegaprojectCategory.PORT,
                List.of(ProjectMetricType.CONTAINER_CAPACITY, ProjectMetricType.ANNUAL_CARGO_TONNES));
        return Map.copyOf(priorities);
    }
}
