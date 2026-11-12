package io.clueterra.budget.generation.question.generator;

import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.cost;
import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.metric;
import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.milestone;
import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.source;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectMetricType;
import io.clueterra.budget.project.ProjectMilestoneType;

class BudgetQuestionPayloadFactoryTest {

    @Test
    @DisplayName("prompt labels only verified completion or opening dates as completion years")
    void promptSeparatesProjectYearFromVerifiedCompletionYear() {
        var source = source(1, 10, MegaprojectCategory.BRIDGE, new BigDecimal("150000000"));
        var actual = milestone(source.projectId(), ProjectMilestoneType.ACTUAL_COMPLETION, 2022);
        var opening = milestone(source.projectId(), ProjectMilestoneType.OPENING, 2023);
        var factory = new BudgetQuestionPayloadFactory(new ObjectMapper());

        var withCompletion = new BudgetQuestionGenerationContext(
                source, List.of(), List.of(), List.of(opening, actual));
        var withoutCompletion = new BudgetQuestionGenerationContext(
                source, List.of(), List.of(), List.of());

        assertThat(factory.prompt(withCompletion).project().projectYear()).isEqualTo(2020);
        assertThat(factory.prompt(withCompletion).project().completionYear()).isEqualTo(2022);
        assertThat(factory.prompt(withCompletion).project().openingYear()).isEqualTo(2023);
        assertThat(factory.reveal(withCompletion).at("/project/completionYear").asInt()).isEqualTo(2022);
        assertThat(factory.prompt(withoutCompletion).project().projectYear()).isEqualTo(2020);
        assertThat(factory.prompt(withoutCompletion).project().completionYear()).isNull();
    }

    @Test
    @DisplayName("should preserve source attribution and derived facts in the reveal payload")
    void shouldPreserveSourceAttributionAndDerivedFactsInRevealPayload() {
        var source = source(
                1, 10, MegaprojectCategory.BRIDGE, new BigDecimal("150000000"));
        var original = cost(
                source.projectId(),
                ProjectCostKind.ORIGINAL_BUDGET,
                new BigDecimal("100000000"),
                new BigDecimal("100000000"));
        var length = metric(
                source.projectId(), ProjectMetricType.LENGTH, new BigDecimal("3900"), "M");
        var planned = milestone(
                source.projectId(), ProjectMilestoneType.PLANNED_COMPLETION, 2018);
        var actual = milestone(
                source.projectId(), ProjectMilestoneType.ACTUAL_COMPLETION, 2020);
        var context = new BudgetQuestionGenerationContext(
                source, List.of(original), List.of(length), List.of(planned, actual));

        JsonNode reveal = new BudgetQuestionPayloadFactory(new ObjectMapper()).reveal(context);

        assertThat(reveal.at("/project/descriptionAttribution/sourceName").textValue())
                .isEqualTo(source.descriptionSourceName());
        assertThat(reveal.at("/project/descriptionAttribution/licenseUrl").textValue())
                .isEqualTo(source.descriptionLicenseUrl());
        assertThat(reveal.at("/actualCost/sourceUrl").textValue())
                .isEqualTo(source.costSourceUrl());
        assertThat(reveal.at("/costs/0/sourceUrl").textValue())
                .isEqualTo(original.sourceUrl());
        assertThat(reveal.at("/metrics/0/referenceUrl").textValue())
                .isEqualTo(length.referenceUrl());
        assertThat(reveal.at("/milestones/0/sourceName").textValue())
                .isEqualTo(planned.sourceName());
        assertThat(reveal.at("/budgetOverrunPercent").decimalValue())
                .isEqualByComparingTo("50.000000");
        assertThat(reveal.at("/delayMonths").longValue()).isEqualTo(24L);
    }
}
