package io.clueterra.budget.generation.question.service;

import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.cost;
import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.metric;
import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.source;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.generation.question.generator.BudgetQuestionPayloadFactory;
import io.clueterra.budget.generation.question.generator.HigherLowerQuestionGenerator;
import io.clueterra.budget.generation.question.generator.HowMuchQuestionGenerator;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectMetricType;

class BudgetQuestionExperienceEnricherTest {

    private BudgetQuestionExperienceEnricher enricher;
    private BudgetQuestionPayloadFactory payloadFactory;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        payloadFactory = new BudgetQuestionPayloadFactory(objectMapper);
        enricher = new BudgetQuestionExperienceEnricher(objectMapper);
    }

    @Test
    @DisplayName("should add useful hints without duplicating a direct USD answer")
    void shouldEnrichNumericQuestionWithoutRepeatingVisiblePromptFacts() {
        var first = context(1, 101, "1000000000", "1600");
        var second = context(2, 102, "1500000000", "2100");
        var contexts = List.of(first, second);
        var generated = new HowMuchQuestionGenerator(payloadFactory).generate(contexts);

        var enriched = enricher.enrich(generated, contexts).getFirst();
        var hints = enriched.revealPayload().path("hints");

        assertThat(hints.size()).isEqualTo(2);
        assertThat(hints.get(0).path("type").asText()).isEqualTo("COMPARABLE_PROJECT");
        assertThat(hints.get(0).path("projectName").asText()).isEqualTo("Project 102");
        assertThat(hints.get(0).path("metricType").asText()).isEqualTo("LENGTH");
        assertThat(hints.get(0).has("completionYear")).isFalse();
        assertThat(hints.get(1).path("type").asText()).isEqualTo("ANSWER_RANGE");
        assertThat(hints.get(1).path("minimum").decimalValue()).isLessThan(new BigDecimal("1000000000"));
        assertThat(hints.get(1).path("maximum").decimalValue()).isGreaterThan(new BigDecimal("1000000000"));

        assertThat(enriched.revealPayload().has("explanation")).isFalse();
    }

    @Test
    @DisplayName("should omit hints entirely for higher-lower questions")
    void shouldNotOfferHintsForHigherLower() {
        var first = context(1, 101, "1000000000", "1600");
        var second = context(2, 102, "1500000000", "2100");
        var contexts = List.of(first, second);
        var generated = new HigherLowerQuestionGenerator(payloadFactory).generate(contexts);

        var enriched = enricher.enrich(generated, contexts).getFirst();

        assertThat(enriched.revealPayload().path("hints").size()).isZero();
        assertThat(enriched.revealPayload().has("explanation")).isFalse();
    }

    private BudgetQuestionGenerationContext context(
            long questionId, long projectId, String answer, String lengthMeters) {
        var source = source(
                questionId,
                projectId,
                MegaprojectCategory.BRIDGE,
                new BigDecimal(answer));
        return new BudgetQuestionGenerationContext(
                source,
                List.of(cost(
                        projectId,
                        ProjectCostKind.REPORTED_COST,
                        new BigDecimal(answer),
                        new BigDecimal(answer))),
                List.of(metric(
                        projectId,
                        ProjectMetricType.LENGTH,
                        new BigDecimal(lengthMeters),
                        "M")),
                List.of());
    }
}
