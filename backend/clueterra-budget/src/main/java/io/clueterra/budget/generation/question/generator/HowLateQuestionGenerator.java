package io.clueterra.budget.generation.question.generator;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import io.clueterra.budget.generation.question.model.BudgetGeneratedQuestionDraft;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HowLateQuestionGenerator implements BudgetQuestionVariantGenerator {

    private final BudgetQuestionPayloadFactory payloadFactory;

    @Override
    public BudgetQuestionType type() {
        return BudgetQuestionType.HOW_LATE;
    }

    @Override
    public List<BudgetGeneratedQuestionDraft> generate(
            List<BudgetQuestionGenerationContext> contexts) {
        return contexts.stream()
                .filter(context -> BudgetQuestionComputation.delay(context).isPresent())
                .map(context -> new BudgetGeneratedQuestionDraft(
                        context.source().questionId(),
                        null,
                        type(),
                        BudgetGeneratedQuestionDraft.unaryKey(
                                type(), context.source().questionId()),
                        BigDecimal.valueOf(
                                BudgetQuestionComputation.delay(context).orElseThrow().months()),
                        "MONTHS",
                        null,
                        payloadFactory.prompt(context),
                        payloadFactory.reveal(context),
                        BudgetQuestionComputation.promote(context.source().difficulty(), 1)))
                .toList();
    }
}
