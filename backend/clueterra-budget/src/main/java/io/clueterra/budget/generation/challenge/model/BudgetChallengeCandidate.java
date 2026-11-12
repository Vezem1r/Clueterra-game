package io.clueterra.budget.generation.challenge.model;

import java.util.Set;

import io.clueterra.budget.generation.question.model.BudgetQuestionType;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;

public record BudgetChallengeCandidate(
        long variantId,
        BudgetQuestionType questionType,
        BudgetDifficulty difficulty,
        long sourceProjectId,
        Long comparisonProjectId,
        MegaprojectCategory category) {

    public Set<Long> projectIds() {
        return comparisonProjectId == null
                ? Set.of(sourceProjectId)
                : Set.of(sourceProjectId, comparisonProjectId);
    }
}
