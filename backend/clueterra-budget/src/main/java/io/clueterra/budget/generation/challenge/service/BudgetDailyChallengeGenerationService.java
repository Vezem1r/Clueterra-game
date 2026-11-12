package io.clueterra.budget.generation.challenge.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import io.clueterra.budget.config.BudgetGenerationProperties;
import io.clueterra.budget.generation.challenge.model.BudgetChallengeCandidate;
import io.clueterra.budget.generation.challenge.model.BudgetChallengeGenerationSummary;
import io.clueterra.budget.generation.challenge.model.BudgetChallengeGenerationTrigger;
import io.clueterra.budget.generation.challenge.model.BudgetChallengePublication;
import io.clueterra.budget.generation.challenge.repository.BudgetDailyChallengeRepository;
import io.clueterra.budget.generation.question.service.BudgetQuestionGenerationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetDailyChallengeGenerationService {

    private final BudgetDailyChallengeRepository repository;
    private final BudgetChallengeQuestionSelector selector;
    private final BudgetQuestionGenerationService questionGenerationService;
    private final BudgetGenerationProperties properties;
    private final Clock clock;

    public BudgetChallengeGenerationSummary generateNow() {
        LocalDate today = LocalDate.now(clock);
        questionGenerationService.generate();
        return generate(today, today.plusDays(7), BudgetChallengeGenerationTrigger.MANUAL);
    }

    public BudgetChallengeGenerationSummary generateRollingWindow() {
        LocalDate today = LocalDate.now(clock);
        questionGenerationService.generate();
        return generate(
                today,
                today.plusDays(7),
                BudgetChallengeGenerationTrigger.SCHEDULED);
    }

    private BudgetChallengeGenerationSummary generate(
            LocalDate fromDate,
            LocalDate toDate,
            BudgetChallengeGenerationTrigger trigger) {
        List<BudgetChallengeCandidate> candidates = repository.findCandidates();
        List<LocalDate> createdDates = new ArrayList<>();
        int skipped = 0;

        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            if (repository.existsByDate(date)) {
                skipped++;
                continue;
            }

            Set<Long> recentProjects = repository.findUsedProjectIds(
                    date.minusDays(properties.recentProjectExclusionDays()), date.minusDays(1));
            List<BudgetChallengeCandidate> selected =
                    selector.select(date, candidates, recentProjects);
            BudgetChallengePublication publication = repository.create(
                    date,
                    trigger,
                    selected.stream().map(BudgetChallengeCandidate::variantId).toList());
            if (publication.created()) {
                createdDates.add(date);
            } else {
                skipped++;
            }
        }

        int requested = Math.toIntExact(toDate.toEpochDay() - fromDate.toEpochDay() + 1);
        BudgetChallengeGenerationSummary summary = new BudgetChallengeGenerationSummary(
                fromDate,
                toDate,
                requested,
                createdDates.size(),
                skipped,
                createdDates);
        log.info(
                "Budget daily challenge generation completed: range {}..{}, {} created, {} occupied",
                fromDate,
                toDate,
                summary.createdChallenges(),
                summary.skippedChallenges());
        return summary;
    }
}
