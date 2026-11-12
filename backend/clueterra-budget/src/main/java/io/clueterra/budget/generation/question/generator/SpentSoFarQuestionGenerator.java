package io.clueterra.budget.generation.question.generator;

import java.util.List;

import org.springframework.stereotype.Component;

import io.clueterra.budget.generation.question.model.BudgetGeneratedQuestionDraft;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;
import io.clueterra.budget.project.ProjectCostKind;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SpentSoFarQuestionGenerator implements BudgetQuestionVariantGenerator {

    private final BudgetQuestionPayloadFactory payloadFactory;

    @Override
    public BudgetQuestionType type() {
        return BudgetQuestionType.SPENT_SO_FAR;
    }

    @Override
    public List<BudgetGeneratedQuestionDraft> generate(
            List<BudgetQuestionGenerationContext> contexts) {
        return contexts.stream()
                .filter(context -> context.source().lifecycleStatus().isUnfinished())
                .filter(context -> context.cost(ProjectCostKind.EXPENDITURE_TO_DATE).isPresent())
                .map(context -> new BudgetGeneratedQuestionDraft(
                        context.source().questionId(),
                        null,
                        type(),
                        BudgetGeneratedQuestionDraft.unaryKey(type(), context.source().questionId()),
                        context.cost(ProjectCostKind.EXPENDITURE_TO_DATE).orElseThrow().midpointUsd(),
                        "USD",
                        null,
                        payloadFactory.prompt(context),
                        payloadFactory.reveal(context),
                        BudgetQuestionComputation.promote(context.source().difficulty(), 1)))
                .toList();
    }
}
