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
public class BudgetOverrunQuestionGenerator implements BudgetQuestionVariantGenerator {

    private static final BigDecimal MINIMUM_OVERRUN_PERCENT = BigDecimal.TEN;

    private final BudgetQuestionPayloadFactory payloadFactory;

    @Override
    public BudgetQuestionType type() {
        return BudgetQuestionType.BUDGET_OVERRUN;
    }

    @Override
    public List<BudgetGeneratedQuestionDraft> generate(
            List<BudgetQuestionGenerationContext> contexts) {
        return contexts.stream()
                .filter(context -> BudgetQuestionComputation.budgetOverrunPercent(context)
                        .filter(value -> value.compareTo(MINIMUM_OVERRUN_PERCENT) >= 0)
                        .isPresent())
                .map(context -> new BudgetGeneratedQuestionDraft(
                        context.source().questionId(),
                        null,
                        type(),
                        BudgetGeneratedQuestionDraft.unaryKey(
                                type(), context.source().questionId()),
                        BudgetQuestionComputation.budgetOverrunPercent(context).orElseThrow(),
                        "PERCENT",
                        null,
                        payloadFactory.prompt(context),
                        payloadFactory.reveal(context),
                        BudgetQuestionComputation.promote(context.source().difficulty(), 1)))
                .toList();
    }
}
