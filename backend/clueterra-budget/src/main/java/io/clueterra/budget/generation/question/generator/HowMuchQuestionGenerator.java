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
public class HowMuchQuestionGenerator implements BudgetQuestionVariantGenerator {

    private final BudgetQuestionPayloadFactory payloadFactory;

    @Override
    public BudgetQuestionType type() {
        return BudgetQuestionType.HOW_MUCH;
    }

    @Override
    public List<BudgetGeneratedQuestionDraft> generate(
            List<BudgetQuestionGenerationContext> contexts) {
        return contexts.stream()
                .filter(context -> !context.source().lifecycleStatus().isUnfinished())
                .filter(context -> context.source().answerCostKind() != ProjectCostKind.CURRENT_ESTIMATE)
                .map(context -> new BudgetGeneratedQuestionDraft(
                        context.source().questionId(),
                        null,
                        type(),
                        BudgetGeneratedQuestionDraft.unaryKey(
                                type(), context.source().questionId()),
                        context.source().answerCostUsd(),
                        "USD",
                        null,
                        payloadFactory.prompt(context),
                        payloadFactory.reveal(context),
                        context.source().difficulty()))
                .toList();
    }
}
