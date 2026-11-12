package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.mapper.AiChangeMapper;
import io.clueterra.questions.ai.model.changeset.AiChangeItem;
import io.clueterra.questions.ai.model.changeset.AiChangeItemStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeItemType;
import io.clueterra.questions.ai.model.changeset.AiChangeSet;
import io.clueterra.questions.ai.model.changeset.AiGeneratedHintProposal;
import io.clueterra.questions.ai.model.changeset.AiProposalStatus;
import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.model.generation.AiGenerationEmbeddingAssessment;
import io.clueterra.questions.ai.model.generation.AiGeneratedImageKind;
import io.clueterra.questions.ai.model.generation.AiGenerationImageAssessment;
import io.clueterra.questions.ai.model.generation.AiHintGenerationBatchResult;
import io.clueterra.questions.ai.model.generation.AiHintGenerationProposalResult;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import io.clueterra.questions.ai.service.change.AiChangeSnapshotFactory;
import io.clueterra.questions.ai.service.support.AiHintSourceIndicator;
import io.clueterra.questions.ai.service.support.AiStageExecutionSupport;
import io.clueterra.questions.ai.tooling.AiImageStorageToolService;
import io.clueterra.questions.ai.tooling.dto.AiStoredImageToolResponse;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.CountryRepository;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.service.deduplication.DeduplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiGenerationProposalProcessor {

    private final AiProperties aiProperties;
    private final AiExecutionRepository executionRepository;
    private final AiStageExecutionSupport stageExecutionSupport;
    private final AiImageStorageToolService imageStorageToolService;
    private final AiChangeMapper changeMapper;
    private final AiChangeSnapshotFactory snapshotFactory;
    private final HintRepository hintRepository;
    private final CountryRepository countryRepository;
    private final DeduplicationService deduplicationService;
    private final AiGenerationProposalScoringService scoringService;
    private final AiGenerationHintHeuristics heuristics;
    private final AiGenerationImagePolicy imagePolicy;
    private final AiGenerationTaxonomyNormalizer taxonomyNormalizer;
    private final Clock clock;

    public AiGenerationProposalProcessor(
            AiProperties aiProperties,
            AiExecutionRepository executionRepository,
            AiStageExecutionSupport stageExecutionSupport,
            AiImageStorageToolService imageStorageToolService,
            AiChangeMapper changeMapper,
            AiChangeSnapshotFactory snapshotFactory,
            HintRepository hintRepository,
            CountryRepository countryRepository,
            DeduplicationService deduplicationService,
            AiGenerationProposalScoringService scoringService,
            AiGenerationHintHeuristics heuristics,
            AiGenerationImagePolicy imagePolicy,
            AiGenerationTaxonomyNormalizer taxonomyNormalizer,
            Clock clock) {
        this.aiProperties = aiProperties;
        this.executionRepository = executionRepository;
        this.stageExecutionSupport = stageExecutionSupport;
        this.imageStorageToolService = imageStorageToolService;
        this.changeMapper = changeMapper;
        this.snapshotFactory = snapshotFactory;
        this.hintRepository = hintRepository;
        this.countryRepository = countryRepository;
        this.deduplicationService = deduplicationService;
        this.scoringService = scoringService;
        this.heuristics = heuristics;
        this.imagePolicy = imagePolicy;
        this.taxonomyNormalizer = taxonomyNormalizer;
        this.clock = clock;
    }

    public AiProposalProcessingResult processProposals(
            List<AiGenerationCountryGapCandidate> countries,
            AiHintGenerationBatchResult batchResult,
            AiRun run,
            AiRunStage stage,
            Map<Integer, AiGenerationEmbeddingAssessment> embeddingAssessments,
            Map<Integer, AiGenerationImageAssessment> imageAssessments) {
        List<Long> countryIds = countries.stream().map(AiGenerationCountryGapCandidate::countryId).toList();
        Set<Long> batchCountryIds = countries.stream().map(AiGenerationCountryGapCandidate::countryId).collect(Collectors.toSet());
        Map<Long, AiGenerationCountryGapCandidate> countriesById = countries.stream()
                .collect(Collectors.toMap(AiGenerationCountryGapCandidate::countryId, Function.identity()));
        Map<Long, Set<String>> contentHashesByCountry = countries.stream()
                .collect(Collectors.toMap(
                        AiGenerationCountryGapCandidate::countryId,
                        country -> hintRepository.findContentHashesByCountryId(country.countryId())
                ));
        Map<Long, List<String>> activeHintTextsByCountry = countries.stream()
                .collect(Collectors.toMap(
                        AiGenerationCountryGapCandidate::countryId,
                        country -> new ArrayList<>(hintRepository.findActiveTextsByCountryId(country.countryId()))
                ));
        Map<Long, List<String>> proposalTextsByCountry = executionRepository.findProposalTextsByCountryIds(countryIds);
        Map<Long, List<String>> batchTextsByCountry = new LinkedHashMap<>();
        Map<Long, Integer> acceptedCountsByCountry = new LinkedHashMap<>();

        int generatedItems = 0;
        int changedItems = 0;
        int duplicateItems = 0;
        int rejectedItems = 0;
        AiChangeSet changeSet = executionRepository.findChangeSetByRunId(run.id()).orElse(null);
        Map<String, Integer> generatedByCountry = new LinkedHashMap<>();

        List<AiHintGenerationProposalResult> proposalResults = safeItems(batchResult);
        for (int proposalIndex = 0; proposalIndex < proposalResults.size(); proposalIndex++) {
            AiHintGenerationProposalResult proposalResult = proposalResults.get(proposalIndex);
            if (proposalResult.countryId() == null || !batchCountryIds.contains(proposalResult.countryId())) {
                continue;
            }

            AiGenerationCountryGapCandidate countryGap = countriesById.get(proposalResult.countryId());
            Country country = countryRepository.findById(countryGap.countryId()).orElse(null);
            String hintText = stageExecutionSupport.blankToNull(proposalResult.hintText());
            String category = taxonomyNormalizer.normalizeCategory(proposalResult.category());
            String hintType = taxonomyNormalizer.normalizeHintType(proposalResult.hintType());
            String evidenceSummary = stageExecutionSupport.blankToNull(proposalResult.evidenceSummary());
            String duplicateSummary = stageExecutionSupport.blankToNull(proposalResult.duplicateCheckSummary());
            AiGenerationImageAssessment imageAssessment = imageAssessments.get(proposalIndex);
            AiGeneratedImageKind declaredImageKind = parseImageKind(proposalResult.imageKind());
            boolean proposedImage = stageExecutionSupport.blankToNull(proposalResult.imageSourceUrl()) != null;
            boolean imageValidated = imageAssessment != null && imageAssessment.validated();
            if (imagePolicy.shouldRetainAsText(
                    declaredImageKind,
                    proposedImage,
                    imageValidated,
                    imageAssessment != null ? imageAssessment.imageKind() : null,
                    category,
                    hintType,
                    heuristics.referencesImage(hintText))) {
                hintType = HintType.ABSOLUTE_FACT.name();
                declaredImageKind = null;
                proposedImage = false;
                imageValidated = false;
                imageAssessment = null;
                duplicateSummary = "Image unavailable or rejected; retained as a standalone text clue";
            }
            String imageSourceUrl = imageValidated
                    && imageAssessment.resolvedImage() != null
                    ? imageAssessment.resolvedImage().imageUrl()
                    : null;
            AiGenerationEmbeddingAssessment embeddingAssessment = embeddingAssessments.get(proposalIndex);
            boolean embeddingValidated = embeddingAssessment != null
                    && embeddingAssessment.checked()
                    && !embeddingAssessment.conflict();
            Integer difficulty = clampDifficulty(scoringService.recalibrateDifficulty(
                    country, category, hintType, hintText, proposalResult.difficulty()
            ));
            Double qualityScore = clampScore(scoringService.recalibrateQuality(
                    hintText, evidenceSummary, duplicateSummary, proposalResult.qualityScore(), category
            ));

            AiProposalStatus status;
            if (hintText == null || category == null || hintType == null || !isKnownCategory(category) || !isKnownHintType(hintType)) {
                status = AiProposalStatus.REJECTED;
                duplicateSummary = "Rejected because category or hintType is not supported after normalization";
                rejectedItems++;
            } else if (!embeddingValidated) {
                status = AiProposalStatus.REJECTED;
                duplicateSummary = embeddingAssessment != null
                        ? embeddingAssessment.validationSummary()
                        : "Rejected because mandatory local embedding validation was not completed";
                rejectedItems++;
            } else if (scoringService.containsCountryName(hintText, country)) {
                status = AiProposalStatus.REJECTED;
                duplicateSummary = "Rejected because hint text reveals the answer country";
                rejectedItems++;
            } else if (heuristicsRejectsProposal(hintText, qualityScore, category)) {
                status = AiProposalStatus.REJECTED;
                duplicateSummary = "Rejected by generation policy as low-signal or weak";
                rejectedItems++;
            } else if (isDuplicateProposal(
                    contentHashesByCountry.getOrDefault(countryGap.countryId(), Set.of()),
                    hintText,
                    activeHintTextsByCountry.getOrDefault(countryGap.countryId(), List.of()),
                    proposalTextsByCountry.getOrDefault(countryGap.countryId(), List.of()),
                    batchTextsByCountry.getOrDefault(countryGap.countryId(), List.of()))) {
                status = AiProposalStatus.DUPLICATE;
                duplicateSummary = "Rejected as an exact duplicate or existing semantic match";
                duplicateItems++;
            } else if (acceptedCountsByCountry.getOrDefault(countryGap.countryId(), 0) >= requestedProposalCount()) {
                status = AiProposalStatus.REJECTED;
                duplicateSummary = "Rejected because the country already reached its requested proposal count";
                rejectedItems++;
            } else if (isSpecializedImageKind(declaredImageKind) && !proposedImage) {
                status = AiProposalStatus.REJECTED;
                duplicateSummary = "Rejected because the requested specialized image could not be found";
                rejectedItems++;
            } else if (proposedImage && !imageValidated) {
                status = AiProposalStatus.REJECTED;
                duplicateSummary = imageAssessment != null
                        ? imageAssessment.validationSummary()
                        : "Rejected because the proposed image did not pass visual validation";
                rejectedItems++;
            } else if (imageValidated && !imagePolicy.isKindCompatible(imageAssessment.imageKind(), category, hintType)) {
                status = AiProposalStatus.REJECTED;
                duplicateSummary = "Rejected because imageKind, category and hintType are inconsistent";
                rejectedItems++;
            } else if (imageSourceUrl == null && heuristics.referencesImage(hintText)) {
                status = AiProposalStatus.REJECTED;
                duplicateSummary = "Rejected because the hint text refers to an image but no Wikimedia image resolved";
                rejectedItems++;
            } else {
                status = AiProposalStatus.PENDING_APPROVAL;
            }

            if (hintText != null) {
                batchTextsByCountry
                        .computeIfAbsent(countryGap.countryId(), ignored -> new ArrayList<>())
                        .add(hintText);
            }

            String storedImageUrl = null;
            if (status == AiProposalStatus.PENDING_APPROVAL && imageSourceUrl != null) {
                AiStoredImageToolResponse storedImage = imageStorageToolService.storeCandidateImageInternal(
                        countryGap.countryCode(),
                        imageSourceUrl,
                        "proposal-" + countryGap.countryCode() + "-" + System.nanoTime()
                );
                storedImageUrl = stageExecutionSupport.blankToNull(storedImage.storedUrl());
                if (storedImageUrl == null) {
                    if (!heuristics.referencesImage(hintText)) {
                        imageSourceUrl = null;
                        imageAssessment = null;
                        declaredImageKind = null;
                        hintType = HintType.ABSOLUTE_FACT.name();
                        duplicateSummary = "Approved image could not be stored; retained as a standalone text clue";
                    } else {
                        status = AiProposalStatus.REJECTED;
                        duplicateSummary = stageExecutionSupport.firstNonBlank(
                                storedImage.message(),
                                "Rejected because the approved image could not be stored"
                        );
                        rejectedItems++;
                    }
                }
            }

            if (status == AiProposalStatus.PENDING_APPROVAL && changeSet == null) {
                changeSet = executionRepository.saveChangeSet(
                        changeMapper.toDraftChangeSet(run, "AI generated hints", "Draft generated hints pending approval"));
            }

            Long approvalChangeSetId = status == AiProposalStatus.PENDING_APPROVAL && changeSet != null ? changeSet.id() : null;

            AiGeneratedHintProposal savedProposal = executionRepository.saveGeneratedHintProposal(new AiGeneratedHintProposal(
                    null,
                    run.id(),
                    stage.id(),
                    approvalChangeSetId,
                    countryGap.countryId(),
                    status,
                    category,
                    hintType,
                    hintText,
                    imageSourceUrl,
                    storedImageUrl,
                    difficulty,
                    qualityScore,
                    "AI_GENERATED",
                    AiHintSourceIndicator.forGenerated(category, imageAssessment != null
                            ? imageAssessment.imageKind()
                            : declaredImageKind),
                    null,
                    stageExecutionSupport.blankToNull(proposalResult.factSummary()),
                    stageExecutionSupport.blankToNull(proposalResult.evidenceSummary()),
                    duplicateSummary,
                    proposalResult.searchTerms() != null ? proposalResult.searchTerms() : List.of(),
                    Instant.now(clock)
            ));

            if (status == AiProposalStatus.PENDING_APPROVAL) {
                acceptedCountsByCountry.merge(countryGap.countryId(), 1, Integer::sum);
                executionRepository.saveChangeItem(new AiChangeItem(
                        null,
                        approvalChangeSetId,
                        run.id(),
                        savedProposal.id(),
                        AiChangeItemType.HINT_CREATE,
                        AiChangeItemStatus.PENDING,
                        null,
                        savedProposal.countryId(),
                        stageExecutionSupport.firstNonBlank(savedProposal.factSummary(), "Generated hint proposal"),
                        "New hint proposal",
                        null,
                        snapshotFactory.snapshotGeneratedProposal(
                                savedProposal,
                                country,
                                stageExecutionSupport.firstNonBlank(savedProposal.storedImageUrl(), savedProposal.imageSourceUrl()),
                                embeddingValidated,
                                embeddingValidated ? aiProperties.getEmbedding().getModel() : null,
                                imageAssessment),
                        Instant.now(clock),
                        null
                ));
                generatedItems++;
                changedItems++;
                generatedByCountry.merge(countryGap.countryCode(), 1, Integer::sum);
            }
        }

        if (changeSet != null) {
            stageExecutionSupport.refreshDraftChangeSet(changeSet);
        }

        return new AiProposalProcessingResult(
                generatedItems,
                changedItems,
                duplicateItems,
                rejectedItems,
                generatedByCountry,
                changeSet
        );
    }

    private List<AiHintGenerationProposalResult> safeItems(AiHintGenerationBatchResult result) {
        return result != null && result.proposals() != null ? result.proposals() : List.of();
    }

    private boolean heuristicsRejectsProposal(String hintText, Double qualityScore, String category) {
        if (hintText == null || hintText.isBlank()) {
            return true;
        }
        boolean boringCategory = category != null
                && (category.equals("EDUCATION") || category.equals("HEALTH"));
        return (qualityScore != null && qualityScore < 45.0)
                || (hintText.length() < 32)
                || (qualityScore != null && qualityScore < 86.0 && heuristics.isGenericThemeHint(hintText))
                || (qualityScore != null && qualityScore < 82.0 && heuristics.isBareStatisticClue(hintText))
                || (qualityScore != null && qualityScore < 75.0 && boringCategory)
                || (aiProperties.getGeneration().getTargetImageShare() <= 0.0 && hintText.toLowerCase().contains("look at this image"))
                || (qualityScore != null && qualityScore < 80.0 && heuristics.hasRepetitiveOpener(hintText));
    }

    private boolean isDuplicateProposal(
            Set<String> contentHashes,
            String hintText,
            List<String> activeHintTexts,
            List<String> proposalTexts,
            List<String> batchTexts) {
        String semanticHash = deduplicationService.generateSemanticHash(hintText);
        if (semanticHash != null && contentHashes.contains(semanticHash)) {
            return true;
        }

        List<String> comparisonPool = new ArrayList<>();
        comparisonPool.addAll(activeHintTexts);
        comparisonPool.addAll(proposalTexts);
        comparisonPool.addAll(batchTexts);
        return deduplicationService.isSemanticallyDuplicate(hintText, comparisonPool);
    }

    private int requestedProposalCount() {
        return aiProperties.getGeneration().getRequestedProposalsPerCountry();
    }

    private boolean isKnownCategory(String value) {
        try {
            Category.valueOf(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isKnownHintType(String value) {
        try {
            HintType.valueOf(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private AiGeneratedImageKind parseImageKind(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AiGeneratedImageKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isSpecializedImageKind(AiGeneratedImageKind imageKind) {
        return imageKind == AiGeneratedImageKind.LANDMARK || imageKind == AiGeneratedImageKind.CURRENCY;
    }

    private Integer clampDifficulty(Integer difficulty) {
        if (difficulty == null) {
            return 50;
        }
        return Math.clamp(difficulty, 1, 100);
    }

    private Double clampScore(Double score) {
        if (score == null) {
            return null;
        }
        return Math.clamp(score, 0.0, 100.0);
    }

}
