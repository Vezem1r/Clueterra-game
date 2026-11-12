package io.clueterra.budget.generation.question.generator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.clueterra.budget.generation.question.model.BudgetGeneratedQuestionDraft;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HigherLowerQuestionGenerator implements BudgetQuestionVariantGenerator {

    private static final BigDecimal MINIMUM_COST_RATIO = new BigDecimal("1.20");

    private final BudgetQuestionPayloadFactory payloadFactory;

    @Override
    public BudgetQuestionType type() {
        return BudgetQuestionType.HIGHER_LOWER;
    }

    @Override
    public List<BudgetGeneratedQuestionDraft> generate(
            List<BudgetQuestionGenerationContext> contexts) {
        Map<String, List<BudgetQuestionGenerationContext>> byCategoryAndCostKind =
                contexts.stream()
                        .collect(Collectors.groupingBy(context ->
                                context.source().category().name()
                                        + ":"
                                        + context.source().answerCostKind().name()));
        List<BudgetGeneratedQuestionDraft> variants = new ArrayList<>();

        byCategoryAndCostKind.values().forEach(category -> generateCategory(category, variants));
        return List.copyOf(variants);
    }

    private void generateCategory(
            List<BudgetQuestionGenerationContext> category,
            List<BudgetGeneratedQuestionDraft> variants) {
        List<BudgetQuestionGenerationContext> sorted = category.stream()
                .sorted(Comparator.comparing(context -> context.source().answerCostUsd()))
                .toList();

        for (int index = 0; index < sorted.size() - 1; index++) {
            BudgetQuestionGenerationContext cheaper = sorted.get(index);
            findOpponent(sorted, index, cheaper)
                    .ifPresent(expensive -> variants.add(createPair(cheaper, expensive)));
        }
    }

    private Optional<BudgetQuestionGenerationContext> findOpponent(
            List<BudgetQuestionGenerationContext> sorted,
            int cheaperIndex,
            BudgetQuestionGenerationContext cheaper) {
        BigDecimal minimumCost = cheaper.source().answerCostUsd().multiply(MINIMUM_COST_RATIO);
        return sorted.subList(cheaperIndex + 1, sorted.size()).stream()
                .filter(candidate -> candidate.source().answerCostUsd().compareTo(minimumCost) >= 0)
                .findFirst();
    }

    private BudgetGeneratedQuestionDraft createPair(
            BudgetQuestionGenerationContext cheaper,
            BudgetQuestionGenerationContext expensive) {
        boolean expensiveFirst = (cheaper.source().questionId() + expensive.source().questionId()) % 2 == 0;
        BudgetQuestionGenerationContext primary = expensiveFirst ? expensive : cheaper;
        BudgetQuestionGenerationContext comparison = expensiveFirst ? cheaper : expensive;
        String answer = expensiveFirst ? "PRIMARY" : "COMPARISON";

        return new BudgetGeneratedQuestionDraft(
                primary.source().questionId(),
                comparison.source().questionId(),
                type(),
                BudgetGeneratedQuestionDraft.comparisonKey(
                        primary.source().questionId(),
                        comparison.source().questionId()),
                null,
                null,
                answer,
                payloadFactory.comparisonPrompt(primary, comparison),
                payloadFactory.comparisonReveal(primary, comparison),
                BudgetQuestionComputation.max(
                        primary.source().difficulty(), comparison.source().difficulty()));
    }
}
