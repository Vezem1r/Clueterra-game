package io.clueterra.budget.generation.challenge.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.clueterra.budget.generation.challenge.model.BudgetChallengeCandidate;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;

@Component
public class BudgetChallengeQuestionSelector {

    public static final int QUESTIONS_PER_CHALLENGE = 5;

    private static final List<Set<BudgetQuestionType>> PREFERRED_SLOTS = List.of(
            EnumSet.of(BudgetQuestionType.HOW_MUCH, BudgetQuestionType.CURRENT_ESTIMATE),
            EnumSet.of(BudgetQuestionType.HIGHER_LOWER),
            EnumSet.of(BudgetQuestionType.COST_PER_UNIT),
            EnumSet.of(
                    BudgetQuestionType.BUDGET_OVERRUN,
                    BudgetQuestionType.ORIGINAL_BUDGET,
                    BudgetQuestionType.SPENT_SO_FAR),
            EnumSet.of(BudgetQuestionType.HOW_LATE, BudgetQuestionType.ORIGINAL_BUDGET));

    public List<BudgetChallengeCandidate> select(
            LocalDate challengeDate,
            List<BudgetChallengeCandidate> candidates,
            Set<Long> recentlyUsedProjectIds) {
        List<BudgetChallengeCandidate> ranked = candidates.stream()
                .sorted(Comparator.comparingLong(candidate -> score(challengeDate, candidate.variantId())))
                .toList();
        List<BudgetChallengeCandidate> selected = new ArrayList<>(QUESTIONS_PER_CHALLENGE);
        Set<Long> usedProjects = new HashSet<>();
        Set<MegaprojectCategory> usedCategories = EnumSet.noneOf(MegaprojectCategory.class);
        Set<BudgetDifficulty> usedDifficulties = EnumSet.noneOf(BudgetDifficulty.class);

        for (Set<BudgetQuestionType> preferredTypes : PREFERRED_SLOTS) {
            choose(
                            ranked,
                            preferredTypes,
                            recentlyUsedProjectIds,
                            usedProjects,
                            usedCategories,
                            usedDifficulties,
                            true)
                    .or(() -> choose(
                            ranked,
                            EnumSet.allOf(BudgetQuestionType.class),
                            recentlyUsedProjectIds,
                            usedProjects,
                            usedCategories,
                            usedDifficulties,
                            true))
                    .or(() -> choose(
                            ranked,
                            preferredTypes,
                            recentlyUsedProjectIds,
                            usedProjects,
                            usedCategories,
                            usedDifficulties,
                            false))
                    .or(() -> choose(
                            ranked,
                            EnumSet.allOf(BudgetQuestionType.class),
                            recentlyUsedProjectIds,
                            usedProjects,
                            usedCategories,
                            usedDifficulties,
                            false))
                    .ifPresent(candidate -> add(
                            candidate,
                            selected,
                            usedProjects,
                            usedCategories,
                            usedDifficulties));
        }

        if (selected.size() != QUESTIONS_PER_CHALLENGE) {
            throw new IllegalStateException(
                    "At least five non-overlapping active BudgetGuessr questions are required");
        }
        return List.copyOf(selected);
    }

    private Optional<BudgetChallengeCandidate> choose(
            List<BudgetChallengeCandidate> candidates,
            Set<BudgetQuestionType> allowedTypes,
            Set<Long> recentlyUsedProjectIds,
            Set<Long> usedProjects,
            Set<MegaprojectCategory> usedCategories,
            Set<BudgetDifficulty> usedDifficulties,
            boolean excludeRecent) {
        return candidates.stream()
                .filter(candidate -> allowedTypes.contains(candidate.questionType()))
                .filter(candidate -> candidate.projectIds().stream().noneMatch(usedProjects::contains))
                .filter(candidate -> !excludeRecent
                        || candidate.projectIds().stream().noneMatch(recentlyUsedProjectIds::contains))
                .min(Comparator
                        .comparing((BudgetChallengeCandidate candidate) ->
                                usedCategories.contains(candidate.category()))
                        .thenComparing(candidate -> usedDifficulties.contains(candidate.difficulty())));
    }

    private void add(
            BudgetChallengeCandidate candidate,
            List<BudgetChallengeCandidate> selected,
            Set<Long> usedProjects,
            Set<MegaprojectCategory> usedCategories,
            Set<BudgetDifficulty> usedDifficulties) {
        selected.add(candidate);
        usedProjects.addAll(candidate.projectIds());
        usedCategories.add(candidate.category());
        usedDifficulties.add(candidate.difficulty());
    }

    private long score(LocalDate date, long variantId) {
        long value = date.toEpochDay() * 0x9E3779B97F4A7C15L + variantId;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
