package io.clueterra.questions.ai.service.review;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import io.clueterra.questions.ai.assistant.AiHintReviewAssistant;
import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.mapper.AiChangeMapper;
import io.clueterra.questions.ai.model.changeset.AiChangeItem;
import io.clueterra.questions.ai.model.changeset.AiChangeItemStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeItemType;
import io.clueterra.questions.ai.model.changeset.AiChangeSet;
import io.clueterra.questions.ai.model.dispatch.AiProviderDispatchRequest;
import io.clueterra.questions.ai.model.quota.AiReviewQuotaSnapshot;
import io.clueterra.questions.ai.model.review.AiHintReviewBatchResult;
import io.clueterra.questions.ai.model.review.AiHintReviewItemResult;
import io.clueterra.questions.ai.model.review.AiHintReviewResult;
import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.model.review.AiReviewDecision;
import io.clueterra.questions.ai.model.review.AiReviewDecisionResolution;
import io.clueterra.questions.ai.model.review.AiReviewEditValidation;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.model.run.AiStageExecutionResult;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import io.clueterra.questions.ai.repository.AiReviewQueueRepository;
import io.clueterra.questions.ai.service.change.AiChangeSnapshotFactory;
import io.clueterra.questions.ai.service.generation.AiGenerationHintHeuristics;
import io.clueterra.questions.ai.service.support.AiProviderDispatcher;
import io.clueterra.questions.ai.service.support.AiStageExecutionSupport;
import io.clueterra.questions.ai.service.support.AiStructuredOutputParser;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.repository.CountryRepository;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.storage.ImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiReviewStageExecutor {

    private static final Set<AiReviewDecision> UNAVAILABLE_DECISIONS =
            Set.of(AiReviewDecision.REPLACE_IMAGE, AiReviewDecision.EDIT_TEXT_AND_IMAGE);
    private static final String ANSWER_LEAK_REJECTION = "Auto-rejected: proposed text names the target country";

    private final AiProperties aiProperties;
    private final AiReviewQueueRepository reviewQueueRepository;
    private final AiHintReviewAssistant hintReviewAssistant;
    private final AiReviewQuotaService reviewQuotaService;
    private final AiProviderDispatcher providerDispatcher;
    private final AiExecutionRepository executionRepository;
    private final AiStructuredOutputParser structuredOutputParser;
    private final AiChangeSnapshotFactory snapshotFactory;
    private final AiChangeMapper changeMapper;
    private final AiStageExecutionSupport stageExecutionSupport;
    private final HintRepository hintRepository;
    private final CountryRepository countryRepository;
    private final AiGenerationHintHeuristics heuristics;
    private final AiReviewEditValidator editValidator;
    private final AiReviewDecisionPolicy decisionPolicy;
    private final AiReviewQualityAnalyzer qualityAnalyzer;
    private final Optional<ImageStorageService> imageStorageService;
    private final Clock clock;

    public AiReviewStageExecutor(
            AiProperties aiProperties,
            AiReviewQueueRepository reviewQueueRepository,
            AiHintReviewAssistant hintReviewAssistant,
            AiReviewQuotaService reviewQuotaService,
            AiProviderDispatcher providerDispatcher,
            AiExecutionRepository executionRepository,
            AiStructuredOutputParser structuredOutputParser,
            AiChangeSnapshotFactory snapshotFactory,
            AiChangeMapper changeMapper,
            AiStageExecutionSupport stageExecutionSupport,
            HintRepository hintRepository,
            CountryRepository countryRepository,
            AiGenerationHintHeuristics heuristics,
            AiReviewEditValidator editValidator,
            AiReviewDecisionPolicy decisionPolicy,
            AiReviewQualityAnalyzer qualityAnalyzer,
            Optional<ImageStorageService> imageStorageService,
            Clock clock) {
        this.aiProperties = aiProperties;
        this.reviewQueueRepository = reviewQueueRepository;
        this.hintReviewAssistant = hintReviewAssistant;
        this.reviewQuotaService = reviewQuotaService;
        this.providerDispatcher = providerDispatcher;
        this.executionRepository = executionRepository;
        this.structuredOutputParser = structuredOutputParser;
        this.snapshotFactory = snapshotFactory;
        this.changeMapper = changeMapper;
        this.stageExecutionSupport = stageExecutionSupport;
        this.hintRepository = hintRepository;
        this.countryRepository = countryRepository;
        this.heuristics = heuristics;
        this.editValidator = editValidator;
        this.decisionPolicy = decisionPolicy;
        this.qualityAnalyzer = qualityAnalyzer;
        this.imageStorageService = imageStorageService;
        this.clock = clock;
    }

    public AiStageExecutionResult execute(AiRun run, AiRunStage stage) {
        List<Long> hintIds = stageExecutionSupport.extractLongList(stage.payload(), "hintIds");
        List<AiReviewCandidate> candidates = reviewQueueRepository.findByHintIds(hintIds);
        if (candidates.isEmpty()) {
            return new AiStageExecutionResult(0, 0, 0, 0, 0, 0, null, Map.of("message", "No hints resolved for stage"));
        }

        AtomicInteger requestIndex = new AtomicInteger(1);
        List<Content> promptContents = buildPromptContents(run, stage, candidates);
        Result<String> result = providerDispatcher.dispatch(new AiProviderDispatchRequest(
                run,
                stage,
                requestIndex,
                promptContents,
                aiProperties.getReview().isCountFailedRequestsAfterDispatch(),
                shouldCountGroundingPrompt(),
                () -> {
                },
                this::hasDailyRetryCapacity,
                () -> hintReviewAssistant.review(promptContents)
        ));

        AiHintReviewBatchResult batchResult = safeBatchResult(result);
        TokenUsage tokenUsage = result.tokenUsage();
        Map<Long, AiHintReviewItemResult> itemsByHintId = safeItems(batchResult).stream()
                .filter(item -> item.hintId() != null)
                .collect(Collectors.toMap(AiHintReviewItemResult::hintId, Function.identity(), (left, right) -> right));

        Map<Long, Hint> hintsById = hintRepository.findByIds(hintIds).stream()
                .collect(Collectors.toMap(Hint::getId, Function.identity()));

        int changedItems = 0;
        AiChangeSet changeSet = executionRepository.findChangeSetByRunId(run.id()).orElse(null);
        Map<String, Integer> decisionCounts = new LinkedHashMap<>();

        for (AiReviewCandidate candidate : candidates) {
            Hint hint = hintsById.get(candidate.hintId());
            if (hint == null) {
                continue;
            }
            Country country = countryRepository.findById(hint.getCountryId()).orElse(null);
            AiHintReviewItemResult itemResult = itemsByHintId.get(candidate.hintId());
            AiReviewDecisionResolution resolution = decisionPolicy.resolve(candidate, itemResult);
            AiReviewDecision decision = resolution.decision();
            Integer retryAfterDays = decisionPolicy.retryAfterDays(candidate, itemResult, decision);
            if (resolution.policyReason() != null) {
                log.warn("AI review decision normalized for hint {}: {}", candidate.hintId(), resolution.policyReason());
            }
            Long changeSetId = null;
            boolean rejectedAnswerLeak = decision == AiReviewDecision.EDIT_TEXT
                    && editValidator.containsTargetCountry(candidate.countryName(), resolution.proposedText());
            AiReviewEditValidation editValidation = decision == AiReviewDecision.EDIT_TEXT
                    ? rejectedAnswerLeak
                            ? new AiReviewEditValidation(false, null, "target country name in proposed text")
                            : editValidator.validate(candidate, resolution.proposedText())
                    : null;
            decisionCounts.merge(rejectedAnswerLeak ? "REJECTED_ANSWER_LEAK" : decision.name(), 1, Integer::sum);
            if (requiresChangeItem(decision)) {
                if (changeSet == null) {
                    changeSet = executionRepository.saveChangeSet(
                            changeMapper.toDraftChangeSet(run, "AI review proposals", "Draft review changes pending approval"));
                }
                Map<String, Object> original = snapshotFactory.snapshotHint(hint, country);
                Map<String, Object> proposed = decision == AiReviewDecision.ESCALATE
                        ? null
                        : new LinkedHashMap<>(snapshotFactory.snapshotReviewProposal(
                                hint,
                                country,
                                resolution.proposedText(),
                                decision == AiReviewDecision.EDIT_TEXT ? null
                                        : stageExecutionSupport.blankToNull(
                                                itemResult != null ? itemResult.proposedImageUrl() : null),
                        decision == AiReviewDecision.EDIT_TEXT
                                ? clampDifficulty(resolution.proposedDifficulty(), candidate.difficultyModifier())
                                : null,
                                decision == AiReviewDecision.DISABLE ? Boolean.FALSE : null
                        ));
                if (proposed != null && editValidation != null) {
                    proposed.put("autoApplyEligible", editValidation.autoApplyEligible());
                    proposed.put("reviewValidationSummary", editValidation.summary());
                    proposed.put("semanticSimilarity", editValidation.semanticSimilarity());
                    if (rejectedAnswerLeak) {
                        proposed.put("autoRejected", true);
                    }
                }
                if (proposed != null && retryAfterDays != null && !rejectedAnswerLeak) {
                    proposed.put("retryAfterDays", retryAfterDays);
                }
                executionRepository.saveChangeItem(new AiChangeItem(
                        null,
                        changeSet.id(),
                        run.id(),
                        null,
                        mapReviewItemType(decision),
                        rejectedAnswerLeak ? AiChangeItemStatus.REJECTED : AiChangeItemStatus.PENDING,
                        hint.getId(),
                        hint.getCountryId(),
                        rejectedAnswerLeak ? ANSWER_LEAK_REJECTION : summarizeReason(itemResult),
                        buildDiffSummary(original, proposed),
                        original,
                        proposed,
                        Instant.now(clock),
                        rejectedAnswerLeak ? Instant.now(clock) : null
                ));
                changeSetId = changeSet.id();
                changedItems++;
            }

            executionRepository.saveHintReviewResult(new AiHintReviewResult(
                    null,
                    run.id(),
                    stage.id(),
                    hint.getId(),
                    candidate.contentRevisionHash(),
                    rejectedAnswerLeak ? AiReviewDecision.ERROR : decision,
                    resolution.confidence(),
                    rejectedAnswerLeak ? ANSWER_LEAK_REJECTION : summarizeReason(itemResult),
                    itemResult != null ? itemResult.evidenceSummary() : null,
                    resolution.proposedText(),
                    decision == AiReviewDecision.EDIT_TEXT
                            ? clampDifficulty(resolution.proposedDifficulty(), candidate.difficultyModifier())
                            : null,
                    itemResult != null ? stageExecutionSupport.blankToNull(itemResult.proposedImageUrl()) : null,
                    decision == AiReviewDecision.DISABLE && itemResult != null
                            ? stageExecutionSupport.blankToNull(itemResult.disableReason())
                            : null,
                    changeSetId,
                    Instant.now(clock),
                    rejectedAnswerLeak ? null : retryAfterDays
            ));
            if (rejectedAnswerLeak) {
                hintRepository.markReviewFailed(hint.getId(), run.id(), Instant.now(clock));
            } else if (requiresChangeItem(decision)) {
                hintRepository.markReviewActionPending(hint.getId(), run.id(), Instant.now(clock));
            } else if (decision == AiReviewDecision.KEEP) {
                hintRepository.markReviewed(hint.getId(), run.id(), Instant.now(clock), retryAfterDays);
            } else {
                hintRepository.markReviewFailed(hint.getId(), run.id(), Instant.now(clock));
            }
        }

        if (changeSet != null) {
            stageExecutionSupport.refreshDraftChangeSet(changeSet);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchSummary", batchResult.batchSummary());
        summary.put("decisionCounts", decisionCounts);

        return new AiStageExecutionResult(
                candidates.size(),
                changedItems,
                0,
                stageExecutionSupport.tokenCountIn(tokenUsage),
                stageExecutionSupport.tokenCountOut(tokenUsage),
                result.toolExecutions() != null ? result.toolExecutions().size() : 0,
                null,
                summary
        );
    }

    private List<Content> buildPromptContents(AiRun run, AiRunStage stage,
                                              List<AiReviewCandidate> candidates) {
        List<Content> contents = new ArrayList<>();
        List<String> skippedImageSamples = new ArrayList<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run.id());
        payload.put("stageId", stage.id());
        payload.put("allowedDecisions", Arrays.stream(AiReviewDecision.values())
                .filter(decision -> !UNAVAILABLE_DECISIONS.contains(decision))
                .map(Enum::name)
                .toList());
        payload.put("hints", candidates.stream().map(this::toHintPayload).toList());
        contents.add(TextContent.from(stageExecutionSupport.toJson(payload)));
        if (aiProperties.getReview().getTokenization().isAllowImages()) {
            int skippedImages = 0;
            for (AiReviewCandidate candidate : candidates) {
                Optional<ImageContent> image = resolveReviewImage(candidate);
                if (image.isEmpty()) {
                    if (candidate.hasImage()) {
                        skippedImages++;
                        if (skippedImageSamples.size() < 5) {
                            skippedImageSamples.add(candidate.imageUrl());
                        }
                    }
                    continue;
                }
                contents.add(TextContent.from("The next image belongs to hintId=" + candidate.hintId()));
                contents.add(image.get());
            }
            if (skippedImages > 0) {
                log.warn(
                        "AI review stage skipped {} image attachment(s) missing from object storage: runId={}, stageId={}, samples={}",
                        skippedImages,
                        run.id(),
                        stage.id(),
                        skippedImageSamples
                );
            }
        }
        return contents;
    }

    private Map<String, Object> toHintPayload(AiReviewCandidate candidate) {
        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("hintId", candidate.hintId());
        hint.put("countryCode", candidate.countryCode());
        hint.put("countryName", candidate.countryName());
        Map<String, Object> allowedContext = new LinkedHashMap<>();
        allowedContext.put("region", safeContext(candidate.countryName(), candidate.countryRegion()));
        allowedContext.put("subregion", safeContext(candidate.countryName(), candidate.countrySubregion()));
        allowedContext.put("capital", safeContext(candidate.countryName(), candidate.countryCapital()));
        hint.put("allowedContext", allowedContext);
        hint.put("hintText", candidate.hintText());
        hint.put("category", candidate.category() != null ? candidate.category().name() : null);
        hint.put("hintType", candidate.hintType() != null ? candidate.hintType().name() : null);
        hint.put("difficulty", candidate.difficulty());
        hint.put("difficultyFloor", heuristics.difficultyFloorForModifier(candidate.difficultyModifier()));
        hint.put("qualityScore", candidate.qualityScore());
        hint.put("source", candidate.source() != null ? candidate.source().name() : null);
        hint.put("sourceIndicator", candidate.sourceIndicator());
        hint.put("variantRole", variantRole(candidate.sourceIndicator()));
        hint.put("sourceYear", candidate.sourceYear());
        hint.put("hasImage", candidate.hasImage());
        hint.put("serverReviewSignals", qualityAnalyzer.analyze(candidate));
        return hint;
    }

    private String safeContext(String countryName, String value) {
        return editValidator.containsTargetCountry(countryName, value) ? null : value;
    }

    private boolean requiresChangeItem(AiReviewDecision decision) {
        return decision == AiReviewDecision.EDIT_TEXT
                || decision == AiReviewDecision.DISABLE
                || decision == AiReviewDecision.REPLACE_IMAGE
                || decision == AiReviewDecision.EDIT_TEXT_AND_IMAGE
                || decision == AiReviewDecision.ESCALATE;
    }

    private AiChangeItemType mapReviewItemType(AiReviewDecision decision) {
        return switch (decision) {
            case DISABLE -> AiChangeItemType.HINT_DISABLE;
            case REPLACE_IMAGE -> AiChangeItemType.IMAGE_REPLACE;
            case ESCALATE -> AiChangeItemType.HINT_ESCALATE;
            case KEEP, ERROR, EDIT_TEXT, EDIT_TEXT_AND_IMAGE -> AiChangeItemType.HINT_UPDATE;
        };
    }

    private Integer clampDifficulty(Integer difficulty, BigDecimal difficultyModifier) {
        if (difficulty == null) {
            return null;
        }
        return Math.clamp(difficulty, heuristics.difficultyFloorForModifier(difficultyModifier), 100);
    }

    private String variantRole(String sourceIndicator) {
        if (sourceIndicator == null || !sourceIndicator.startsWith("HYDRORIVERS_RIVER_SKELETON_")) {
            return "single";
        }
        if (sourceIndicator.endsWith("_1")) {
            return "easy labelled variant";
        }
        if (sourceIndicator.endsWith("_2")) {
            return "medium partially labelled variant";
        }
        if (sourceIndicator.endsWith("_3")) {
            return "hard unlabelled variant";
        }
        return "single";
    }

    private String buildDiffSummary(Map<String, Object> original, Map<String, Object> proposed) {
        if (proposed == null) {
            return "Manual review requested";
        }
        List<String> changes = new ArrayList<>();
        compareField(changes, "hintText", original, proposed);
        compareField(changes, "imageUrl", original, proposed);
        compareField(changes, "isActive", original, proposed);
        compareField(changes, "difficulty", original, proposed);
        if (proposed.containsKey("retryAfterDays")) {
            changes.add("retry after " + proposed.get("retryAfterDays") + " days");
        }
        return changes.isEmpty() ? "Manual review requested" : String.join("; ", changes);
    }

    private void compareField(List<String> changes, String field, Map<String, Object> original, Map<String, Object> proposed) {
        Object from = original.get(field);
        Object to = proposed.get(field);
        if (!String.valueOf(from).equals(String.valueOf(to))) {
            changes.add(field + ": " + from + " -> " + to);
        }
    }

    private String summarizeReason(AiHintReviewItemResult itemResult) {
        if (itemResult == null) {
            return "AI review result missing";
        }
        return stageExecutionSupport.firstNonBlank(itemResult.summaryReason(), itemResult.evidenceSummary());
    }

    private List<AiHintReviewItemResult> safeItems(AiHintReviewBatchResult result) {
        return result != null && result.items() != null ? result.items() : List.of();
    }

    private AiHintReviewBatchResult safeBatchResult(Result<?> result) {
        try {
            return structuredOutputParser.parseReviewBatchResult(result);
        } catch (IllegalStateException ex) {
            return new AiHintReviewBatchResult(ex.getMessage(), List.of());
        }
    }

    private Optional<ImageContent> resolveReviewImage(AiReviewCandidate candidate) {
        if (imageStorageService.isEmpty()) {
            return Optional.empty();
        }
        ImageStorageService storage = imageStorageService.get();
        String objectPath = storage.extractObjectPath(candidate.imageUrl());
        if (objectPath == null) {
            return Optional.empty();
        }
        return storage.readObjectBytes(objectPath)
                .map(bytes -> ImageContent.from(
                        Base64.getEncoder().encodeToString(bytes),
                        mimeTypeFor(objectPath),
                        ImageContent.DetailLevel.LOW));
    }

    private static String mimeTypeFor(String objectPath) {
        String lower = objectPath.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private boolean shouldCountGroundingPrompt() {
        return aiProperties.reviewModelProfile().allowGoogleSearch()
                && aiProperties.getReview().isCountGroundedPromptsPerRequest();
    }

    private boolean hasDailyRetryCapacity() {
        AiReviewQuotaSnapshot snapshot = reviewQuotaService.getSnapshot();
        return snapshot.requestsTodayRemaining() > 0
                && (!snapshot.googleSearchEnabled()
                || snapshot.groundedPromptsTodayRemaining() == null
                || snapshot.groundedPromptsTodayRemaining() > 0);
    }
}
