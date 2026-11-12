package io.clueterra.budget.generation.question.generator;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.clueterra.budget.generation.question.model.BudgetQuestionCostFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetQuestionMetricFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionMilestoneFact;
import io.clueterra.budget.generation.question.model.BudgetPromptMetric;
import io.clueterra.budget.generation.question.model.BudgetPromptProject;
import io.clueterra.budget.generation.question.model.BudgetQuestionPromptPayload;
import io.clueterra.budget.generation.question.model.BudgetSourceQuestion;
import io.clueterra.budget.project.ProjectMilestoneType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BudgetQuestionPayloadFactory {

    private final ObjectMapper objectMapper;

    public BudgetQuestionPromptPayload prompt(BudgetQuestionGenerationContext context) {
        return new BudgetQuestionPromptPayload(promptProject(context), null, null, null);
    }

    public BudgetQuestionPromptPayload comparisonPrompt(
            BudgetQuestionGenerationContext primary,
            BudgetQuestionGenerationContext comparison) {
        return new BudgetQuestionPromptPayload(
                null,
                promptProject(primary),
                promptProject(comparison),
                null);
    }

    public ObjectNode reveal(BudgetQuestionGenerationContext context) {
        ObjectNode reveal = objectMapper.createObjectNode();
        reveal.set("project", revealProject(context));
        reveal.set("actualCost", actualCost(context.source()));

        ArrayNode costs = reveal.putArray("costs");
        context.costs().forEach(cost -> costs.add(cost(cost)));

        ArrayNode metrics = reveal.putArray("metrics");
        context.metrics().forEach(metric -> metrics.add(metric(metric)));

        ArrayNode milestones = reveal.putArray("milestones");
        context.milestones().forEach(milestone -> milestones.add(milestone(milestone)));

        BudgetQuestionComputation.budgetOverrunPercent(context)
                .ifPresent(value -> reveal.put("budgetOverrunPercent", value));
        BudgetQuestionComputation.delay(context)
                .ifPresent(delay -> reveal.put("delayMonths", delay.months()));
        return reveal;
    }

    public ObjectNode comparisonReveal(
            BudgetQuestionGenerationContext primary,
            BudgetQuestionGenerationContext comparison) {
        ObjectNode reveal = objectMapper.createObjectNode();
        reveal.set("primary", reveal(primary));
        reveal.set("comparison", reveal(comparison));
        return reveal;
    }

    public BudgetQuestionPromptPayload metricPrompt(
            BudgetQuestionGenerationContext context,
            BudgetQuestionMetricFact metric,
            BigDecimal displayValue,
            String displayUnit) {
        return new BudgetQuestionPromptPayload(
                promptProject(context),
                null,
                null,
                new BudgetPromptMetric(metric.type(), displayValue, displayUnit));
    }

    private BudgetPromptProject promptProject(BudgetQuestionGenerationContext context) {
        BudgetSourceQuestion source = context.source();
        return new BudgetPromptProject(
                source.name(),
                source.countryCode(),
                source.countryName(),
                source.regionName(),
                source.category(),
                source.lifecycleStatus(),
                source.projectYear(),
                milestoneYear(context, ProjectMilestoneType.ACTUAL_COMPLETION),
                milestoneYear(context, ProjectMilestoneType.OPENING),
                source.expectedCompletionYear(),
                source.answerCostKind());
    }

    private ObjectNode revealProject(BudgetQuestionGenerationContext context) {
        BudgetSourceQuestion source = context.source();
        ObjectNode project = objectMapper.createObjectNode();
        project.put("name", source.name());
        project.put("description", source.description());
        if (source.descriptionSourceName() != null) {
            ObjectNode attribution = project.putObject("descriptionAttribution");
            attribution.put("sourceName", source.descriptionSourceName());
            attribution.put("sourceUrl", source.descriptionSourceUrl());
            attribution.put("licenseLabel", source.descriptionLicenseLabel());
            attribution.put("licenseUrl", source.descriptionLicenseUrl());
        }
        project.put("countryCode", source.countryCode());
        project.put("countryName", source.countryName());
        project.put("regionName", source.regionName());
        project.put("category", source.category().name());
        project.put("lifecycleStatus", source.lifecycleStatus().name());
        project.put("answerCostKind", source.answerCostKind().name());
        put(project, "projectYear", source.projectYear());
        put(project, "completionYear", milestoneYear(context, ProjectMilestoneType.ACTUAL_COMPLETION));
        put(project, "openingYear", milestoneYear(context, ProjectMilestoneType.OPENING));
        put(project, "expectedCompletionYear", source.expectedCompletionYear());
        return project;
    }

    private Integer milestoneYear(BudgetQuestionGenerationContext context, ProjectMilestoneType type) {
        if (context.source().lifecycleStatus().isUnfinished()) {
            return null;
        }
        return context.milestone(type).map(this::milestoneYear).orElse(null);
    }

    private Integer milestoneYear(BudgetQuestionMilestoneFact milestone) {
        return milestone.date() == null ? milestone.year() : milestone.date().getYear();
    }

    private ObjectNode actualCost(BudgetSourceQuestion source) {
        ObjectNode cost = objectMapper.createObjectNode();
        cost.put("amountUsd", source.answerCostUsd());
        cost.put("sourceName", source.costSourceName());
        cost.put("sourceUrl", source.costSourceUrl());
        put(cost, "referenceUrl", source.costReferenceUrl());
        return cost;
    }

    private ObjectNode cost(BudgetQuestionCostFact fact) {
        ObjectNode cost = objectMapper.createObjectNode();
        cost.put("kind", fact.kind().name());
        cost.put("amountUsdLow", fact.amountUsdLow());
        cost.put("amountUsdHigh", fact.amountUsdHigh());
        put(cost, "year", fact.costYear());
        source(cost, fact.sourceName(), fact.sourceUrl(), fact.referenceUrl());
        return cost;
    }

    private ObjectNode metric(BudgetQuestionMetricFact fact) {
        ObjectNode metric = objectMapper.createObjectNode();
        metric.put("type", fact.type().name());
        metric.put("value", fact.normalizedValue());
        metric.put("unit", fact.normalizedUnit());
        source(metric, fact.sourceName(), fact.sourceUrl(), fact.referenceUrl());
        return metric;
    }

    private ObjectNode milestone(BudgetQuestionMilestoneFact fact) {
        ObjectNode milestone = objectMapper.createObjectNode();
        milestone.put("type", fact.type().name());
        put(milestone, "date", fact.date() == null ? null : fact.date().toString());
        put(milestone, "year", fact.year());
        source(milestone, fact.sourceName(), fact.sourceUrl(), fact.referenceUrl());
        return milestone;
    }

    private void source(ObjectNode node, String name, String url, String referenceUrl) {
        node.put("sourceName", name);
        node.put("sourceUrl", url);
        put(node, "referenceUrl", referenceUrl);
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
}
