package io.clueterra.budget.generation.question.service;

import java.util.EnumMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.budget.generation.question.generator.BudgetQuestionVariantGenerator;
import io.clueterra.budget.generation.question.model.BudgetGeneratedQuestionDraft;
import io.clueterra.budget.generation.question.model.BudgetQuestionCostFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationResult;
import io.clueterra.budget.generation.question.model.BudgetQuestionMetricFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionMilestoneFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;
import io.clueterra.budget.generation.question.model.BudgetQuestionVariantReplacement;
import io.clueterra.budget.generation.question.repository.BudgetQuestionGenerationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetQuestionGenerationService {

    private final BudgetQuestionGenerationRepository repository;
    private final List<BudgetQuestionVariantGenerator> generators;
    private final BudgetQuestionExperienceEnricher experienceEnricher;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public BudgetQuestionGenerationResult generate() {
        List<BudgetQuestionGenerationContext> contexts = loadContexts();
        Map<String, BudgetGeneratedQuestionDraft> variants = generators.stream()
                .sorted(Comparator.comparing(generator -> generator.type().ordinal()))
                .flatMap(generator -> generator.generate(contexts).stream())
                .collect(Collectors.toMap(
                        BudgetGeneratedQuestionDraft::generationKey,
                        Function.identity(),
                        (first, duplicate) -> {
                            throw new IllegalStateException(
                                    "Duplicate Budget question generation key: "
                                            + first.generationKey());
                        },
                        LinkedHashMap::new));

        List<BudgetGeneratedQuestionDraft> generated = experienceEnricher.enrich(
                List.copyOf(variants.values()), contexts);
        BudgetQuestionVariantReplacement replacement = repository.replaceVariants(generated);
        Map<BudgetQuestionType, Long> byType = generated.stream()
                .collect(Collectors.groupingBy(
                        BudgetGeneratedQuestionDraft::questionType,
                        () -> new EnumMap<>(BudgetQuestionType.class),
                        Collectors.counting()));

        log.info(
                "Budget question generation completed: {} variants, {} new, {} retired; by type {}",
                replacement.generatedCount(),
                replacement.createdCount(),
                replacement.retiredCount(),
                byType);
        return new BudgetQuestionGenerationResult(
                replacement.generatedCount(),
                replacement.createdCount(),
                replacement.retiredCount(),
                byType);
    }

    private List<BudgetQuestionGenerationContext> loadContexts() {
        Map<Long, List<BudgetQuestionCostFact>> costs = repository.findCostFacts().stream()
                .collect(Collectors.groupingBy(BudgetQuestionCostFact::projectId));
        Map<Long, List<BudgetQuestionMetricFact>> metrics = repository.findMetricFacts().stream()
                .collect(Collectors.groupingBy(BudgetQuestionMetricFact::projectId));
        Map<Long, List<BudgetQuestionMilestoneFact>> milestones =
                repository.findMilestoneFacts().stream()
                        .collect(Collectors.groupingBy(BudgetQuestionMilestoneFact::projectId));

        return repository.findSourceQuestions().stream()
                .map(source -> new BudgetQuestionGenerationContext(
                        source,
                        costs.getOrDefault(source.projectId(), List.of()),
                        metrics.getOrDefault(source.projectId(), List.of()),
                        milestones.getOrDefault(source.projectId(), List.of())))
                .toList();
    }
}
