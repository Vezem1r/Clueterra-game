package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.model.generation.AiGenerationEmbeddingAssessment;
import io.clueterra.questions.ai.model.generation.AiGenerationEmbeddingMatch;
import io.clueterra.questions.ai.model.generation.AiGenerationEmbeddingValidationResult;
import io.clueterra.questions.ai.model.generation.AiHintGenerationBatchResult;
import io.clueterra.questions.ai.model.generation.AiHintGenerationProposalResult;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import io.clueterra.questions.ai.service.embedding.AiHintEmbeddingService;
import io.clueterra.questions.ai.service.embedding.CosineSimilarity;
import io.clueterra.questions.ai.tooling.dto.AiGeneratedHintProposalToolSummary;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.repository.HintRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiGenerationEmbeddingValidationService {

    private static final int MAX_RECENT_PROPOSALS_PER_COUNTRY = 30;
    private static final int MAX_MATCHES_PER_PROPOSAL = 3;

    private final HintRepository hintRepository;
    private final AiExecutionRepository executionRepository;
    private final Optional<AiHintEmbeddingService> embeddingService;

    public void requireReady(List<AiGenerationCountryGapCandidate> countries) {
        if (embeddingService.isEmpty() || !embeddingService.get().isAvailable()) {
            throw new IllegalStateException("Mandatory local embedding validation is unavailable");
        }

        AiHintEmbeddingService service = embeddingService.get();
        countries.forEach(country -> {
            List<Hint> activeHints = hintRepository.findActiveByCountryId(country.countryId());
            Map<Long, float[]> activeVectors = service.vectorsForCountry(country.countryId());
            if (hasMissingActiveVectors(activeHints, activeVectors)) {
                service.backfillCountry(country.countryId());
                activeVectors = service.vectorsForCountry(country.countryId());
            }
            if (hasMissingActiveVectors(activeHints, activeVectors)) {
                throw new IllegalStateException(
                        "Mandatory local embedding validation has incomplete active-hint coverage for countryId="
                                + country.countryId());
            }
        });
    }

    public AiGenerationEmbeddingValidationResult validate(
            List<AiGenerationCountryGapCandidate> countries,
            AiHintGenerationBatchResult batchResult) {
        return validate(countries, batchResult, embeddingService
                .map(AiHintEmbeddingService::relatedThreshold)
                .orElse(1.0));
    }

    public AiGenerationEmbeddingValidationResult validateForPersistence(
            List<AiGenerationCountryGapCandidate> countries,
            AiHintGenerationBatchResult batchResult) {
        return validate(countries, batchResult, embeddingService
                .map(AiHintEmbeddingService::duplicateThreshold)
                .orElse(1.0));
    }

    private AiGenerationEmbeddingValidationResult validate(
            List<AiGenerationCountryGapCandidate> countries,
            AiHintGenerationBatchResult batchResult,
            double conflictThreshold) {
        List<AiHintGenerationProposalResult> proposals = safeProposals(batchResult);
        Set<Long> allowedCountryIds = countries.stream()
                .map(AiGenerationCountryGapCandidate::countryId)
                .collect(Collectors.toSet());
        List<Integer> proposalIndexes = IntStream.range(0, proposals.size())
                .filter(index -> isEmbeddable(proposals.get(index), allowedCountryIds))
                .boxed()
                .toList();

        if (proposalIndexes.isEmpty()) {
            return new AiGenerationEmbeddingValidationResult(List.of());
        }

        if (embeddingService.isEmpty() || !embeddingService.get().isAvailable()) {
            return unavailableAssessments(proposals, proposalIndexes, "Local embedding service is unavailable");
        }

        AiHintEmbeddingService service = embeddingService.get();
        List<float[]> proposalVectors = service.embedAllForGenerationValidation(proposalIndexes.stream()
                .map(index -> proposals.get(index).hintText())
                .toList());
        if (proposalVectors.size() != proposalIndexes.size()) {
            return unavailableAssessments(proposals, proposalIndexes, "Local embedding service did not return every proposal vector");
        }

        Map<Integer, float[]> proposalVectorsByIndex = IntStream.range(0, proposalIndexes.size())
                .boxed()
                .collect(Collectors.toMap(
                        proposalIndexes::get,
                        proposalVectors::get,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, List<Hint>> activeHintsByCountry = loadActiveHints(countries);
        Map<Long, Map<Long, float[]>> activeVectorsByCountry = loadActiveVectors(countries, service);
        Map<Long, List<AiGeneratedHintProposalToolSummary>> recentProposalsByCountry = loadRecentProposals(countries);
        List<AiGeneratedHintProposalToolSummary> recentProposals = recentProposalsByCountry.values().stream()
                .flatMap(List::stream)
                .filter(proposal -> StringUtils.isNotBlank(proposal.hintText()))
                .toList();
        List<float[]> recentVectors = recentProposals.isEmpty()
                ? List.of()
                : service.embedAllForGenerationValidation(recentProposals.stream()
                        .map(AiGeneratedHintProposalToolSummary::hintText)
                        .toList());
        if (recentVectors.size() != recentProposals.size()) {
            return unavailableAssessments(proposals, proposalIndexes, "Local embedding service did not return every recent proposal vector");
        }
        Map<Long, float[]> recentVectorsById = IntStream.range(0, recentProposals.size())
                .boxed()
                .collect(Collectors.toMap(
                        index -> recentProposals.get(index).id(),
                        recentVectors::get,
                        (left, right) -> left,
                        LinkedHashMap::new));

        Optional<Long> incompleteCountryId = countries.stream()
                .map(AiGenerationCountryGapCandidate::countryId)
                .filter(countryId -> hasMissingActiveVectors(
                        activeHintsByCountry.getOrDefault(countryId, List.of()),
                        activeVectorsByCountry.getOrDefault(countryId, Map.of())))
                .findFirst();
        if (incompleteCountryId.isPresent()) {
            return unavailableAssessments(
                    proposals,
                    proposalIndexes,
                    "Active hint embedding coverage is incomplete for countryId=" + incompleteCountryId.get());
        }

        List<AiGenerationEmbeddingAssessment> assessments = proposalIndexes.stream()
                .map(index -> assess(
                        index,
                        proposals,
                        proposalIndexes,
                        proposalVectorsByIndex,
                        activeHintsByCountry,
                        activeVectorsByCountry,
                        recentProposalsByCountry,
                        recentVectorsById,
                        conflictThreshold))
                .toList();
        return new AiGenerationEmbeddingValidationResult(assessments);
    }

    private AiGenerationEmbeddingAssessment assess(
            int proposalIndex,
            List<AiHintGenerationProposalResult> proposals,
            List<Integer> proposalIndexes,
            Map<Integer, float[]> proposalVectorsByIndex,
            Map<Long, List<Hint>> activeHintsByCountry,
            Map<Long, Map<Long, float[]>> activeVectorsByCountry,
            Map<Long, List<AiGeneratedHintProposalToolSummary>> recentProposalsByCountry,
            Map<Long, float[]> recentVectorsById,
            double conflictThreshold) {
        AiHintGenerationProposalResult proposal = proposals.get(proposalIndex);
        float[] proposalVector = proposalVectorsByIndex.get(proposalIndex);
        List<AiGenerationEmbeddingMatch> matches = new ArrayList<>();

        for (Hint hint : activeHintsByCountry.getOrDefault(proposal.countryId(), List.of())) {
            addMatchIfConflicting(
                    matches,
                    "ACTIVE_HINT",
                    hint.getId(),
                    null,
                    hint.getHintText(),
                    proposalVector,
                    activeVectorsByCountry.getOrDefault(proposal.countryId(), Map.of()).get(hint.getId()),
                    conflictThreshold);
        }
        for (AiGeneratedHintProposalToolSummary recent : recentProposalsByCountry.getOrDefault(
                proposal.countryId(), List.of())) {
            addMatchIfConflicting(
                    matches,
                    "RECENT_PROPOSAL",
                    recent.id(),
                    null,
                    recent.hintText(),
                    proposalVector,
                    recentVectorsById.get(recent.id()),
                    conflictThreshold);
        }
        proposalIndexes.stream()
                .filter(otherIndex -> otherIndex < proposalIndex)
                .filter(otherIndex -> proposal.countryId().equals(proposals.get(otherIndex).countryId()))
                .forEach(otherIndex -> addMatchIfConflicting(
                        matches,
                        "CURRENT_BATCH",
                        null,
                        otherIndex,
                        proposals.get(otherIndex).hintText(),
                        proposalVector,
                        proposalVectorsByIndex.get(otherIndex),
                        conflictThreshold));

        List<AiGenerationEmbeddingMatch> topMatches = matches.stream()
                .sorted(Comparator.comparingDouble(AiGenerationEmbeddingMatch::similarity).reversed())
                .limit(MAX_MATCHES_PER_PROPOSAL)
                .toList();
        boolean conflict = !topMatches.isEmpty();
        double highestSimilarity = conflict ? topMatches.getFirst().similarity() : 0.0;
        String validationSummary = conflict
                ? "Embedding conflict: nearest semantic similarity=" + formatScore(highestSimilarity)
                : "Mandatory local embedding validation passed";
        return new AiGenerationEmbeddingAssessment(
                proposalIndex,
                proposal.countryId(),
                proposal.hintText(),
                true,
                conflict,
                highestSimilarity,
                validationSummary,
                topMatches);
    }

    private void addMatchIfConflicting(
            List<AiGenerationEmbeddingMatch> matches,
            String sourceType,
            Long sourceId,
            Integer sourceProposalIndex,
            String hintText,
            float[] proposalVector,
            float[] existingVector,
            double conflictThreshold) {
        if (StringUtils.isBlank(hintText) || existingVector == null) {
            return;
        }
        double similarity = CosineSimilarity.between(proposalVector, existingVector);
        if (similarity >= conflictThreshold) {
            matches.add(new AiGenerationEmbeddingMatch(
                    sourceType,
                    sourceId,
                    sourceProposalIndex,
                    hintText,
                    similarity));
        }
    }

    private Map<Long, List<Hint>> loadActiveHints(List<AiGenerationCountryGapCandidate> countries) {
        return countries.stream().collect(Collectors.toMap(
                AiGenerationCountryGapCandidate::countryId,
                country -> hintRepository.findActiveByCountryId(country.countryId()),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private Map<Long, Map<Long, float[]>> loadActiveVectors(
            List<AiGenerationCountryGapCandidate> countries,
            AiHintEmbeddingService service) {
        return countries.stream().collect(Collectors.toMap(
                AiGenerationCountryGapCandidate::countryId,
                country -> service.vectorsForCountry(country.countryId()),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private Map<Long, List<AiGeneratedHintProposalToolSummary>> loadRecentProposals(
            List<AiGenerationCountryGapCandidate> countries) {
        return countries.stream().collect(Collectors.toMap(
                AiGenerationCountryGapCandidate::countryId,
                country -> executionRepository.findProposalSummariesByCountryId(
                        country.countryId(), MAX_RECENT_PROPOSALS_PER_COUNTRY),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private boolean hasMissingActiveVectors(List<Hint> activeHints, Map<Long, float[]> vectors) {
        return activeHints.stream()
                .filter(hint -> StringUtils.isNotBlank(hint.getHintText()))
                .map(Hint::getId)
                .anyMatch(id -> !vectors.containsKey(id));
    }

    private AiGenerationEmbeddingValidationResult unavailableAssessments(
            List<AiHintGenerationProposalResult> proposals,
            List<Integer> proposalIndexes,
            String summary) {
        return new AiGenerationEmbeddingValidationResult(proposalIndexes.stream()
                .map(index -> new AiGenerationEmbeddingAssessment(
                        index,
                        proposals.get(index).countryId(),
                        proposals.get(index).hintText(),
                        false,
                        false,
                        0.0,
                        summary,
                        List.of()))
                .toList());
    }

    private boolean isEmbeddable(AiHintGenerationProposalResult proposal, Set<Long> allowedCountryIds) {
        return proposal != null
                && proposal.countryId() != null
                && allowedCountryIds.contains(proposal.countryId())
                && StringUtils.isNotBlank(proposal.hintText());
    }

    private List<AiHintGenerationProposalResult> safeProposals(AiHintGenerationBatchResult result) {
        return result != null && result.proposals() != null ? result.proposals() : List.of();
    }

    private String formatScore(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
