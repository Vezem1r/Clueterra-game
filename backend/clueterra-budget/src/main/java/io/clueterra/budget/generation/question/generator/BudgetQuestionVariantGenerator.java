package io.clueterra.budget.generation.question.generator;

import java.util.List;

import io.clueterra.budget.generation.question.model.BudgetGeneratedQuestionDraft;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;

public interface BudgetQuestionVariantGenerator {

    BudgetQuestionType type();

    List<BudgetGeneratedQuestionDraft> generate(List<BudgetQuestionGenerationContext> contexts);
}
