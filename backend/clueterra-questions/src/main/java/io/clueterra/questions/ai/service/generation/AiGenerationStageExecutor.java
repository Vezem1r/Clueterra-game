package io.clueterra.questions.ai.service.generation;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.Result;
import io.clueterra.questions.ai.assistant.AiPromptLoader;
import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.changeset.AiGeneratedHintProposal;
import io.clueterra.questions.ai.model.changeset.AiProposalStatus;
import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.model.generation.AiGenerationEmbeddingAssessment;
import io.clueterra.questions.ai.model.generation.AiGenerationEmbeddingValidationResult;
import io.clueterra.questions.ai.model.generation.AiGeneratedImageKind;
import io.clueterra.questions.ai.model.generation.AiHintGenerationBatchResult;
import io.clueterra.questions.ai.model.generation.AiHintGenerationProposalResult;
import io.clueterra.questions.ai.model.generation.AiGenerationImageAssessment;
import io.clueterra.questions.ai.model.generation.AiGenerationSpecializedImageRequest;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.model.run.AiStageExecutionResult;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import io.clueterra.questions.ai.repository.AiGenerationGapRepository;
import io.clueterra.questions.ai.service.support.AiStageExecutionSupport;
import io.clueterra.questions.ai.service.support.AiStructuredOutputParser;
import io.clueterra.questions.ai.service.support.AiHintSourceIndicator;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.CountryRepository;
import io.clueterra.questions.repository.HintRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiGenerationStageExecutor {

    private final AiProperties aiProperties;
    private final AiGenerationGapRepository generationGapRepository;
    private final AiStructuredOutputParser structuredOutputParser;
    private final AiExecutionRepository executionRepository;
    private final AiStageExecutionSupport stageExecutionSupport;
    private final HintRepository hintRepository;
    private final CountryRepository countryRepository;
    private final AiGenerationHintHeuristics heuristics;
    private final AiGenerationModelGateway modelGateway;
    private final AiGenerationProposalProcessor proposalProcessor;
    private final AiGenerationEmbeddingValidationService embeddingValidationService;
    private final AiGenerationImageValidationService imageValidationService;
    private final AiGenerationSpecializedImagePlanner specializedImagePlanner;
    private final AiPromptLoader promptLoader;
    private final Clock clock;

    public AiGenerationStageExecutor(
            AiProperties aiProperties,
            AiGenerationGapRepository generationGapRepository,
            AiStructuredOutputParser structuredOutputParser,
            AiExecutionRepository executionRepository,
            AiStageExecutionSupport stageExecutionSupport,
            HintRepository hintRepository,
            CountryRepository countryRepository,
            AiGenerationHintHeuristics heuristics,
            AiGenerationModelGateway modelGateway,
            AiGenerationProposalProcessor proposalProcessor,
            AiGenerationEmbeddingValidationService embeddingValidationService,
            AiGenerationImageValidationService imageValidationService,
            AiGenerationSpecializedImagePlanner specializedImagePlanner,
            AiPromptLoader promptLoader,
            Clock clock) {
        this.aiProperties = aiProperties;
        this.generationGapRepository = generationGapRepository;
        this.structuredOutputParser = structuredOutputParser;
        this.executionRepository = executionRepository;
        this.stageExecutionSupport = stageExecutionSupport;
        this.hintRepository = hintRepository;
        this.countryRepository = countryRepository;
        this.heuristics = heuristics;
        this.modelGateway = modelGateway;
        this.proposalProcessor = proposalProcessor;
        this.embeddingValidationService = embeddingValidationService;
        this.imageValidationService = imageValidationService;
        this.specializedImagePlanner = specializedImagePlanner;
        this.promptLoader = promptLoader;
        this.clock = clock;
    }

    public AiStageExecutionResult execute(AiRun run, AiRunStage stage) {
        List<Long> countryIds = stageExecutionSupport.extractLongList(stage.payload(), "countryIds");
        List<AiGenerationCountryGapCandidate> countries = generationGapRepository.findByCountryIds(
                countryIds,
                aiProperties.getGeneration().getMinActiveHintsThreshold()
        );
        if (countries.isEmpty()) {
            return new AiStageExecutionResult(0, 0, 0, 0, 0, 0, null, Map.of("message", "No countries resolved for stage"));
        }
        embeddingValidationService.requireReady(countries);

        AtomicInteger requestIndex = new AtomicInteger(1);
        AtomicLong dispatchedInputTokens = new AtomicLong();
        AtomicLong dispatchedOutputTokens = new AtomicLong();
        AtomicInteger dispatchedToolCalls = new AtomicInteger();
        Map<Long, AiGenerationSpecializedImageRequest> specializedImageRequests =
                specializedImagePlanner.plan(countries);
        String payloadJson = buildStagePayloadJson(run, stage, countries, specializedImageRequests);

        // Phase 1: Draft
        List<Content> draftContents = buildPromptContents("generation-phase-draft.txt", payloadJson, null);
        Result<String> draftResult = modelGateway.generate(
                run, stage, requestIndex, draftContents,
                dispatchedInputTokens, dispatchedOutputTokens, dispatchedToolCalls
        );

        AiHintGenerationBatchResult draftBatchResult = safeBatchResult(draftResult);
        AiHintGenerationBatchResult finalBatchResult = draftBatchResult;

        // Phase 2: Revision
        boolean revisionPassUsed = false;
        boolean diversityRecoveryPassUsed = false;
        int embeddingRepairPasses = 0;
        if (!safeItems(finalBatchResult).isEmpty()) {
            List<Content> revisionContents = buildPromptContents("generation-phase-revision.txt", payloadJson,
                    stageExecutionSupport.toJson(finalBatchResult));
            Result<String> revisionResult = modelGateway.generate(
                    run, stage, requestIndex, revisionContents,
                    dispatchedInputTokens, dispatchedOutputTokens, dispatchedToolCalls
            );
            AiHintGenerationBatchResult parsedRevision = safeBatchResult(revisionResult);
            if (!safeItems(parsedRevision).isEmpty()) {
                finalBatchResult = parsedRevision;
            }
            revisionPassUsed = true;
        }

        // Phase 3: Diversity recovery
        if (needsDiversityRecoveryPass(countries, finalBatchResult, specializedImageRequests)) {
            List<Content> diversityContents = buildPromptContents("generation-phase-diversity.txt", payloadJson,
                    stageExecutionSupport.toJson(finalBatchResult));
            Result<String> diversityResult = modelGateway.generate(
                    run, stage, requestIndex, diversityContents,
                    dispatchedInputTokens, dispatchedOutputTokens, dispatchedToolCalls
            );
            AiHintGenerationBatchResult parsedDiversity = safeBatchResult(diversityResult);
            if (!safeItems(parsedDiversity).isEmpty()) {
                finalBatchResult = parsedDiversity;
            }
            diversityRecoveryPassUsed = true;
        }

        AiGenerationEmbeddingValidationResult embeddingValidation =
                embeddingValidationService.validate(countries, finalBatchResult);
        requireCompleteEmbeddingValidation(embeddingValidation, run, stage);
        int maxEmbeddingRepairPasses = Math.max(
                0,
                aiProperties.getGeneration().getMaxEmbeddingRepairPasses());
        while (!embeddingValidation.conflicts().isEmpty()
                && embeddingRepairPasses < maxEmbeddingRepairPasses) {
            List<Integer> conflictIndexes = embeddingValidation.conflicts().stream()
                    .map(AiGenerationEmbeddingAssessment::proposalIndex)
                    .toList();
            List<Content> repairContents = buildPromptContents(
                    "generation-phase-embedding-repair.txt",
                    payloadJson,
                    stageExecutionSupport.toJson(finalBatchResult));
            repairContents.add(TextContent.from(
                    "Embedding repair pass " + (embeddingRepairPasses + 1)
                            + " of " + maxEmbeddingRepairPasses + ".\n"
                            + "Embedding conflicts that must be replaced with genuinely different facts:\n"
                            + stageExecutionSupport.toJson(embeddingValidation.conflicts())));
            Result<String> repairResult = modelGateway.generate(
                    run,
                    stage,
                    requestIndex,
                    repairContents,
                    dispatchedInputTokens,
                    dispatchedOutputTokens,
                    dispatchedToolCalls);
            AiHintGenerationBatchResult repairedBatch = safeBatchResult(repairResult);
            finalBatchResult = mergeEmbeddingRepairs(finalBatchResult, repairedBatch, conflictIndexes);
            embeddingRepairPasses++;
            embeddingValidation = embeddingValidationService.validateForPersistence(countries, finalBatchResult);
            requireCompleteEmbeddingValidation(embeddingValidation, run, stage);
        }

        embeddingValidation = embeddingValidationService.validateForPersistence(countries, finalBatchResult);
        requireCompleteEmbeddingValidation(embeddingValidation, run, stage);

        Map<Integer, AiGenerationImageAssessment> imageAssessments = imageValidationService.validate(
                countries,
                finalBatchResult,
                run,
                stage,
                requestIndex,
                dispatchedInputTokens,
                dispatchedOutputTokens,
                dispatchedToolCalls);

        // Process proposals
        AiProposalProcessingResult processingResult = proposalProcessor.processProposals(
                countries,
                finalBatchResult,
                run,
                stage,
                embeddingValidation.byProposalIndex(),
                imageAssessments
        );
        int unavailableSpecializedImages = recordUnavailableSpecializedImages(
                run,
                stage,
                countries,
                specializedImageRequests,
                finalBatchResult);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("draftBatchSummary", draftBatchResult.batchSummary());
        summary.put("finalBatchSummary", finalBatchResult.batchSummary());
        summary.put("generatedItems", processingResult.generatedItems());
        summary.put("generatedByCountry", processingResult.generatedByCountry());
        summary.put("duplicateItems", processingResult.duplicateItems());
        summary.put("rejectedItems", processingResult.rejectedItems());
        summary.put("revisionPassUsed", revisionPassUsed);
        summary.put("diversityRecoveryPassUsed", diversityRecoveryPassUsed);
        summary.put("embeddingRepairPassUsed", embeddingRepairPasses > 0);
        summary.put("embeddingRepairPasses", embeddingRepairPasses);
        summary.put("embeddingCheckedHints", embeddingValidation.assessments().size());
        summary.put("embeddingConflictsAfterRepair", embeddingValidation.conflicts().size());
        summary.put("visuallyCheckedImages", imageAssessments.size());
        summary.put("visuallyApprovedImages", imageAssessments.values().stream()
                .filter(AiGenerationImageAssessment::validated)
                .count());
        summary.put("unavailableSpecializedImages", unavailableSpecializedImages);

        return new AiStageExecutionResult(
                countries.size(),
                processingResult.changedItems(),
                processingResult.generatedItems(),
                dispatchedInputTokens.get(),
                dispatchedOutputTokens.get(),
                dispatchedToolCalls.get(),
                null,
                summary
        );
    }

    private List<Content> buildPromptContents(String promptFile, String payloadJson, String batchJson) {
        List<Content> contents = new ArrayList<>();
        contents.add(TextContent.from(promptLoader.load(promptFile)));
        if (payloadJson != null) {
            contents.add(TextContent.from(payloadJson));
        }
        if (batchJson != null) {
            contents.add(TextContent.from("Current batch JSON:\n" + batchJson));
        }
        return contents;
    }

    private void requireCompleteEmbeddingValidation(
            AiGenerationEmbeddingValidationResult validation,
            AiRun run,
            AiRunStage stage) {
        if (!validation.hasUnchecked()) {
            return;
        }
        String reason = validation.assessments().stream()
                .filter(assessment -> !assessment.checked())
                .map(AiGenerationEmbeddingAssessment::validationSummary)
                .findFirst()
                .orElse("unknown embedding validation failure");
        throw new IllegalStateException(
                "Mandatory final embedding validation failed for runId=" + run.id()
                        + ", stageId=" + stage.id()
                        + ": " + reason);
    }

    private AiHintGenerationBatchResult mergeEmbeddingRepairs(
            AiHintGenerationBatchResult currentBatch,
            AiHintGenerationBatchResult repairedBatch,
            List<Integer> conflictIndexes) {
        List<AiHintGenerationProposalResult> current = safeItems(currentBatch);
        List<AiHintGenerationProposalResult> repaired = safeItems(repairedBatch);
        if (current.size() != repaired.size()) {
            log.warn(
                    "Ignoring embedding repair with a changed proposal count: expected={}, actual={}",
                    current.size(),
                    repaired.size());
            return currentBatch;
        }

        List<AiHintGenerationProposalResult> merged = new ArrayList<>(current);
        conflictIndexes.stream()
                .filter(index -> index >= 0 && index < repaired.size())
                .forEach(index -> merged.set(index, repaired.get(index)));
        return new AiHintGenerationBatchResult(repairedBatch.batchSummary(), merged);
    }

    private String buildStagePayloadJson(
            AiRun run,
            AiRunStage stage,
            List<AiGenerationCountryGapCandidate> countries,
            Map<Long, AiGenerationSpecializedImageRequest> specializedImageRequests) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run.id());
        payload.put("stageId", stage.id());
        payload.put("requestedProposalsPerCountryFloor", aiProperties.getGeneration().getRequestedProposalsPerCountry());
        payload.put("maxProposalsPerCountry", aiProperties.getGeneration().getMaxProposalsPerCountry());
        payload.put("maxEmbeddingRepairPasses", aiProperties.getGeneration().getMaxEmbeddingRepairPasses());
        payload.put("specializedImageSearchAttempts", aiProperties.getGeneration().getSpecializedImageSearchAttempts());
        payload.put("allowedCategories", Arrays.stream(Category.values()).map(Enum::name).toList());
        payload.put("allowedHintTypes", Arrays.stream(HintType.values()).map(Enum::name).toList());
        payload.put("countries", countries.stream().map(country -> {
            Map<String, Object> countryPayload = new LinkedHashMap<>();
            countryPayload.put("countryId", country.countryId());
            countryPayload.put("countryCode", country.countryCode());
            countryPayload.put("countryName", country.countryName());
            countryPayload.put("activeHintCount", country.activeHintCount());
            countryPayload.put("pendingProposalCount", country.pendingProposalCount());
            countryPayload.put("effectiveHintCount", country.effectiveHintCount());
            countryPayload.put("lastGeneratedAt", country.lastGeneratedAt());
            countryPayload.put("requestedProposalCount", requestedProposalCount());
            countryPayload.put("requestedImageClueCount", heuristics.targetImageClueCount(
                    requestedProposalCount(), aiProperties.getGeneration().getTargetImageShare()));
            AiGenerationSpecializedImageRequest specializedRequest = specializedImageRequests.getOrDefault(
                    country.countryId(),
                    AiGenerationSpecializedImageRequest.none());
            countryPayload.put("requestedLandmarkImageClueCount",
                    specializedRequest.landmarkCount());
            countryPayload.put("requestedCurrencyImageClueCount",
                    specializedRequest.currencyCount());

            Country fullCountry = countryRepository.findById(country.countryId()).orElse(null);
            int difficultyFloor = heuristics.countryDifficultyFloor(fullCountry);
            countryPayload.put("difficultyFloor", difficultyFloor);
            countryPayload.put("difficultyRange", difficultyFloor + "-100");

            Map<String, Integer> categoryCounts = new LinkedHashMap<>(hintRepository.findCategoryCountsByCountryId(country.countryId()));
            Map<String, Integer> pendingProposalCounts = executionRepository.findProposalCategoryCountsByCountryId(country.countryId());
            pendingProposalCounts.forEach((cat, cnt) -> categoryCounts.merge(cat, cnt, Integer::sum));
            countryPayload.put("existingCategoryDistribution", categoryCounts);
            countryPayload.put("pendingProposalCount", pendingProposalCounts.values().stream().mapToInt(Integer::intValue).sum());

            int totalHints = categoryCounts.values().stream().mapToInt(Integer::intValue).sum();
            int numCategories = Category.values().length;
            int weakThreshold = Math.max(1, totalHints / numCategories / 2);
            List<String> weakCategories = Arrays.stream(Category.values())
                    .map(Enum::name)
                    .filter(cat -> categoryCounts.getOrDefault(cat, 0) < weakThreshold)
                    .toList();
            countryPayload.put("weakCategories", weakCategories);

            return countryPayload;
        }).toList());
        return stageExecutionSupport.toJson(payload);
    }

    private boolean needsDiversityRecoveryPass(
            List<AiGenerationCountryGapCandidate> countries,
            AiHintGenerationBatchResult batchResult,
            Map<Long, AiGenerationSpecializedImageRequest> specializedImageRequests) {
        if (safeItems(batchResult).isEmpty()) {
            return false;
        }

        Map<Long, List<AiHintGenerationProposalResult>> proposalsByCountry = safeItems(batchResult).stream()
                .filter(item -> item.countryId() != null)
                .collect(Collectors.groupingBy(AiHintGenerationProposalResult::countryId, LinkedHashMap::new, Collectors.toList()));

        int requestedCount = requestedProposalCount();
        for (AiGenerationCountryGapCandidate country : countries) {
            List<AiHintGenerationProposalResult> proposals = proposalsByCountry.getOrDefault(country.countryId(), List.of());
            if (proposals.isEmpty()) {
                continue;
            }
            Country fullCountry = countryRepository.findById(country.countryId()).orElse(null);
            int countryFloor = heuristics.countryDifficultyFloor(fullCountry);
            int targetDistinctCategories = heuristics.targetDistinctCategoryCount(requestedCount);
            long accessibleCount = proposals.stream()
                    .filter(item -> item.difficulty() != null && item.difficulty() <= heuristics.accessibleDifficultyUpperBound(countryFloor))
                    .count();
            long hardCount = proposals.stream()
                    .filter(item -> item.difficulty() != null && item.difficulty() >= heuristics.hardDifficultyLowerBound(countryFloor))
                    .count();
            long expertCount = proposals.stream()
                    .filter(item -> item.difficulty() != null && item.difficulty() >= heuristics.expertDifficultyLowerBound(countryFloor))
                    .count();
            long distinctCategories = proposals.stream()
                    .map(AiHintGenerationProposalResult::category)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .count();
            long maxCategoryCount = proposals.stream()
                    .map(AiHintGenerationProposalResult::category)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                    .values().stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0);
            long repeatedFamilies = proposals.stream()
                    .map(item -> heuristics.factFamilyKey(item.category(), item.hintType(), item.hintText()))
                    .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                    .values().stream()
                    .filter(count -> count > 1)
                    .count();
            long landmarkImageCount = proposals.stream()
                    .filter(item -> "LANDMARK".equalsIgnoreCase(item.imageKind()))
                    .filter(item -> item.imageSourceUrl() != null && !item.imageSourceUrl().isBlank())
                    .count();
            long currencyImageCount = proposals.stream()
                    .filter(item -> "CURRENCY".equalsIgnoreCase(item.imageKind()))
                    .filter(item -> item.imageSourceUrl() != null && !item.imageSourceUrl().isBlank())
                    .count();
            AiGenerationSpecializedImageRequest specializedRequest = specializedImageRequests.getOrDefault(
                    country.countryId(),
                    AiGenerationSpecializedImageRequest.none());

            if (distinctCategories < Math.min(targetDistinctCategories, proposals.size())
                    || maxCategoryCount > Math.max(3, proposals.size() / 2)
                    || accessibleCount < heuristics.targetAccessibleClueCount(requestedCount, countryFloor)
                    || hardCount < heuristics.targetHardClueCount(requestedCount, countryFloor)
                    || expertCount < heuristics.targetExpertClueCount(requestedCount, countryFloor)
                    || repeatedFamilies > 0
                    || landmarkImageCount < specializedRequest.landmarkCount()
                    || currencyImageCount < specializedRequest.currencyCount()) {
                return true;
            }
        }
        return false;
    }

    private List<AiHintGenerationProposalResult> safeItems(AiHintGenerationBatchResult result) {
        return result != null && result.proposals() != null ? result.proposals() : List.of();
    }

    private int recordUnavailableSpecializedImages(
            AiRun run,
            AiRunStage stage,
            List<AiGenerationCountryGapCandidate> countries,
            Map<Long, AiGenerationSpecializedImageRequest> specializedImageRequests,
            AiHintGenerationBatchResult batchResult) {
        int unavailableCount = 0;
        List<AiHintGenerationProposalResult> proposals = safeItems(batchResult);
        for (AiGenerationCountryGapCandidate country : countries) {
            AiGenerationSpecializedImageRequest request = specializedImageRequests.getOrDefault(
                    country.countryId(),
                    AiGenerationSpecializedImageRequest.none());
            if (request.landmarkCount() > 0
                    && !containsImageKind(proposals, country.countryId(), AiGeneratedImageKind.LANDMARK)) {
                saveUnavailableImageAttempt(run, stage, country, AiGeneratedImageKind.LANDMARK);
                unavailableCount++;
            }
            if (request.currencyCount() > 0
                    && !containsImageKind(proposals, country.countryId(), AiGeneratedImageKind.CURRENCY)) {
                saveUnavailableImageAttempt(run, stage, country, AiGeneratedImageKind.CURRENCY);
                unavailableCount++;
            }
        }
        return unavailableCount;
    }

    private boolean containsImageKind(
            List<AiHintGenerationProposalResult> proposals,
            Long countryId,
            AiGeneratedImageKind imageKind) {
        return proposals.stream()
                .filter(proposal -> countryId.equals(proposal.countryId()))
                .map(AiHintGenerationProposalResult::imageKind)
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> imageKind.name().equalsIgnoreCase(value));
    }

    private void saveUnavailableImageAttempt(
            AiRun run,
            AiRunStage stage,
            AiGenerationCountryGapCandidate country,
            AiGeneratedImageKind imageKind) {
        String subject = imageKind == AiGeneratedImageKind.CURRENCY ? "currency" : "landmark";
        String category = imageKind == AiGeneratedImageKind.CURRENCY
                ? Category.ECONOMY.name()
                : Category.LANDMARKS.name();
        executionRepository.saveGeneratedHintProposal(new AiGeneratedHintProposal(
                null,
                run.id(),
                stage.id(),
                null,
                country.countryId(),
                AiProposalStatus.REJECTED,
                category,
                HintType.IMAGE_FACT.name(),
                "No safe " + subject + " image candidate was available in this generation pass.",
                null,
                null,
                null,
                null,
                "AI_GENERATED",
                AiHintSourceIndicator.forGenerated(category, imageKind),
                null,
                "Specialized image slot unavailable",
                null,
                "Retry suppressed by the specialized image cooldown",
                List.of(),
                Instant.now(clock)));
    }

    private AiHintGenerationBatchResult safeBatchResult(Result<?> result) {
        try {
            return structuredOutputParser.parseGenerationBatchResult(result);
        } catch (IllegalStateException ex) {
            return new AiHintGenerationBatchResult(ex.getMessage(), List.of());
        }
    }

    private int requestedProposalCount() {
        return aiProperties.getGeneration().getRequestedProposalsPerCountry();
    }
}
