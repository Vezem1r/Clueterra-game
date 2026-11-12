package io.clueterra.questions.ai.service.change;

import io.clueterra.questions.ai.dto.changeset.AiChangeSetActionResponse;
import io.clueterra.questions.ai.model.changeset.AiChangeItem;
import io.clueterra.questions.ai.model.changeset.AiChangeItemStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeItemType;
import io.clueterra.questions.ai.model.changeset.AiChangeSet;
import io.clueterra.questions.ai.model.changeset.AiChangeSetStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeSetType;
import io.clueterra.questions.ai.model.changeset.AiGeneratedHintProposal;
import io.clueterra.questions.ai.model.changeset.AiProposalStatus;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import io.clueterra.questions.ai.service.generation.AiWikimediaAttribution;
import io.clueterra.questions.ai.service.generation.AiWikimediaAttributionService;
import io.clueterra.questions.ai.service.embedding.AiHintEmbeddingService;
import io.clueterra.questions.ai.service.support.AiHintSourceIndicator;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.exception.ConflictException;
import io.clueterra.questions.exception.ResourceNotFoundException;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.service.deduplication.DeduplicationService;
import io.clueterra.questions.service.hint.CountryNameMatcher;
import io.clueterra.questions.clients.common.ImageRightsSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class AiChangeSetService {

    private final AiExecutionRepository executionRepository;
    private final HintRepository hintRepository;
    private final DeduplicationService deduplicationService;
    private final AiWikimediaAttributionService wikimediaAttributionService;
    private final Optional<AiHintEmbeddingService> embeddingService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AiChangeSetService(
            AiExecutionRepository executionRepository,
            HintRepository hintRepository,
            DeduplicationService deduplicationService,
            AiWikimediaAttributionService wikimediaAttributionService,
            Optional<AiHintEmbeddingService> embeddingService,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.executionRepository = executionRepository;
        this.hintRepository = hintRepository;
        this.deduplicationService = deduplicationService;
        this.wikimediaAttributionService = wikimediaAttributionService;
        this.embeddingService = embeddingService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public AiChangeSetActionResponse apply(Long changeSetId) {
        AiApplyBatch batch = new AiApplyBatch(prefetchAttributions(changeSetId));
        AiChangeSetActionResponse response = transactionTemplate.execute(status -> applyLocked(changeSetId, batch));
        storeEmbeddings(batch);
        return response;
    }

    private AiChangeSetActionResponse applyLocked(Long changeSetId, AiApplyBatch batch) {
        AiChangeSet changeSet = executionRepository.findChangeSetByIdForUpdate(changeSetId)
                .orElseThrow(() -> new ResourceNotFoundException("AI change set not found: " + changeSetId));
        List<AiChangeItem> items = executionRepository.findItemsByChangeSetIdForUpdate(changeSetId);
        Instant now = Instant.now(clock);
        int appliedNow = 0;

        for (AiChangeItem item : items) {
            if (changeSet.changeSetType() == AiChangeSetType.REVIEW
                    && isAwaitingApply(item.status())
                    && isSupersededReviewItem(item)) {
                cancelSupersededItem(item, now);
                continue;
            }
            if (!isEligibleForManualApply(changeSet, item)) {
                continue;
            }
            if (applyItem(item, now, batch)) {
                appliedNow++;
            }
        }

        return toActionResponse(refreshChangeSet(changeSet, now), appliedNow);
    }

    public AiChangeSetActionResponse applySafeReviewUpdates(Long changeSetId) {
        AiApplyBatch batch = AiApplyBatch.withoutAttributions();
        AiChangeSetActionResponse response = transactionTemplate.execute(status -> applySafeReviewUpdatesLocked(changeSetId, batch));
        storeEmbeddings(batch);
        return response;
    }

    private AiChangeSetActionResponse applySafeReviewUpdatesLocked(Long changeSetId, AiApplyBatch batch) {
        AiChangeSet changeSet = executionRepository.findChangeSetByIdForUpdate(changeSetId)
                .orElseThrow(() -> new ResourceNotFoundException("AI change set not found: " + changeSetId));
        if (changeSet.changeSetType() != AiChangeSetType.REVIEW) {
            throw new IllegalArgumentException("Safe review auto-apply requires a REVIEW change set");
        }

        Instant now = Instant.now(clock);
        int appliedNow = 0;
        for (AiChangeItem item : executionRepository.findItemsByChangeSetIdForUpdate(changeSetId)) {
            if (item.status() != AiChangeItemStatus.PENDING
                    || item.itemType() != AiChangeItemType.HINT_UPDATE
                    || !isTextOnlyReviewUpdate(item)) {
                continue;
            }
            if (isSupersededReviewItem(item)) {
                cancelSupersededItem(item, now);
                continue;
            }
            if (applyItem(item, now, batch)) {
                appliedNow++;
            }
        }
        return toActionResponse(refreshChangeSet(changeSet, now), appliedNow);
    }

    private boolean isTextOnlyReviewUpdate(AiChangeItem item) {
        Map<String, Object> original = item.originalSnapshot();
        Map<String, Object> proposed = item.proposedSnapshot();
        if (original == null || proposed == null) {
            return false;
        }
        String originalText = asString(original.get("hintText"));
        String proposedText = asString(proposed.get("hintText"));
        String countryName = asString(original.get("countryName"));
        return item.proposalId() == null
                && proposedText != null
                && !proposedText.isBlank()
                && Objects.equals(asString(original.get("imageUrl")), asString(proposed.get("imageUrl")))
                && Objects.equals(asBoolean(original.get("isActive")), asBoolean(proposed.get("isActive")))
                && !introducesCountryName(countryName, originalText, proposedText);
    }

    private boolean introducesCountryName(String countryName, String originalText, String proposedText) {
        return !CountryNameMatcher.mentions(originalText, countryName)
                && CountryNameMatcher.mentions(proposedText, countryName);
    }

    private boolean isEligibleForManualApply(AiChangeSet changeSet, AiChangeItem item) {
        if (changeSet.changeSetType() == AiChangeSetType.REVIEW) {
            return item.status() == AiChangeItemStatus.APPROVED;
        }
        return isAwaitingApply(item.status());
    }

    private boolean applyItem(AiChangeItem item, Instant now, AiApplyBatch batch) {
        if (item.itemType() == AiChangeItemType.HINT_ESCALATE) {
            return acknowledgeEscalation(item, now);
        } else if (item.proposalId() != null) {
            applyGeneratedHint(item, now, batch);
            return true;
        } else {
            return applyReviewedHint(item, now, batch);
        }
    }

    private Map<Long, Optional<AiWikimediaAttribution>> prefetchAttributions(Long changeSetId) {
        Map<Long, Optional<AiWikimediaAttribution>> attributions = new HashMap<>();
        for (Long proposalId : executionRepository.findApplicableProposalIds(changeSetId)) {
            executionRepository.findProposalById(proposalId)
                    .filter(proposal -> proposal.storedImageUrl() != null && proposal.imageSourceUrl() != null)
                    .ifPresent(proposal -> attributions.put(proposal.id(),
                            wikimediaAttributionService.fetchAttribution(proposal.imageSourceUrl())));
        }
        return attributions;
    }

    private void storeEmbeddings(AiApplyBatch batch) {
        embeddingService.ifPresent(service -> batch.embeddingTargets().forEach(hint -> {
            try {
                service.storeForHint(hint.getId(), hint.getHintText(), hint.getContentRevisionHash());
            } catch (RuntimeException e) {
                log.warn("Embedding for hint {} not stored; the backfill job will retry", hint.getId(), e);
            }
        }));
    }

    private boolean acknowledgeEscalation(AiChangeItem item, Instant now) {
        if (loadCurrentReviewedHint(item).isEmpty()) {
            cancelSupersededItem(item, now);
            return false;
        }
        hintRepository.markReviewed(item.hintId(), item.runId(), now);
        executionRepository.saveChangeItem(new AiChangeItem(
                item.id(),
                item.changeSetId(),
                item.runId(),
                item.proposalId(),
                item.itemType(),
                AiChangeItemStatus.APPLIED,
                item.hintId(),
                item.countryId(),
                item.reasonSummary(),
                item.diffSummary(),
                item.originalSnapshot(),
                item.proposedSnapshot(),
                item.createdAt(),
                now
        ));
        return true;
    }

    private void applyGeneratedHint(AiChangeItem item, Instant now, AiApplyBatch batch) {
        AiGeneratedHintProposal proposal = executionRepository.findProposalById(item.proposalId())
                .orElseThrow(() -> new ResourceNotFoundException("AI proposal not found: " + item.proposalId()));
        String finalImageUrl = proposal.storedImageUrl();

        if (finalImageUrl != null && !Boolean.TRUE.equals(asBoolean(
                item.proposedSnapshot() != null ? item.proposedSnapshot().get("imageValidated") : null))) {
            throw new ConflictException(
                    "Generated image hint has no successful visual validation for proposal " + proposal.id());
        }

        Optional<AiWikimediaAttribution> attribution = Optional.empty();
        if (finalImageUrl != null) {
            if (proposal.imageSourceUrl() == null) {
                throw new ConflictException(
                        "Generated image proposal has no Wikimedia source URL: " + proposal.id());
            }
            attribution = batch.prefetchedAttribution(proposal.id())
                    .orElseThrow(() -> new ConflictException(
                            "Change set changed while applying; retry the apply for proposal " + proposal.id()));
            if (attribution.isEmpty()) {
                throw new ConflictException(
                        "Wikimedia attribution is no longer available for generated image proposal "
                                + proposal.id());
            }
        }

        if (finalImageUrl != null
                && "CURRENCY".equals(asString(item.proposedSnapshot().get("imageKind")))
                && attribution.filter(value -> ImageRightsSupport.isPublicDomainOrCc0License(
                value.licenseLabel(), value.licenseUrl())).isEmpty()) {
            throw new ConflictException(
                    "Generated currency image is no longer explicitly Public Domain or CC0 for proposal "
                            + proposal.id());
        }

        Hint.HintBuilder hintBuilder = Hint.builder()
                .countryId(proposal.countryId())
                .category(Category.valueOf(proposal.category()))
                .hintType(HintType.valueOf(proposal.hintType()))
                .hintText(proposal.hintText())
                .imageUrl(finalImageUrl)
                .difficulty(clampDifficulty(proposal.difficulty()))
                .qualityScore(toBigDecimal(proposal.qualityScore()))
                .source(DataSource.AI_GENERATED)
                .sourceIndicator(resolveSourceIndicator(proposal))
                .sourceYear(proposal.sourceYear())
                .contentHash(deduplicationService.generateSemanticHash(proposal.hintText()))
                .contentRevisionHash(deduplicationService.generateRevisionHash(proposal.hintText(), finalImageUrl))
                .ambiguityCount(1)
                .timesUsed(0)
                .timesCorrect(0)
                .isActive(true)
                .reviewStatus(HintReviewStatus.REVIEWED)
                .lastReviewedAt(now)
                .lastReviewRunId(item.runId())
                .createdAt(now)
                .updatedAt(now);

        attribution.ifPresent(attr -> hintBuilder
                .originalImageUrl(proposal.imageSourceUrl())
                .imageSourcePageUrl(attr.sourcePageUrl())
                .imageAuthorName(attr.authorName())
                .imageAttributionText(attr.attributionText())
                .imageLicenseLabel(attr.licenseLabel())
                .imageLicenseUrl(attr.licenseUrl()));

        Hint hint = hintBuilder.build();
        Hint persistedHint = hintRepository.save(hint).hint();
        validateGeneratedHintPersistence(persistedHint, proposal, finalImageUrl);
        batch.storeEmbeddingAfterCommit(persistedHint);

        executionRepository.saveGeneratedHintProposal(new AiGeneratedHintProposal(
                proposal.id(),
                proposal.runId(),
                proposal.stageId(),
                proposal.changeSetId(),
                proposal.countryId(),
                AiProposalStatus.APPLIED,
                proposal.category(),
                proposal.hintType(),
                proposal.hintText(),
                proposal.imageSourceUrl(),
                finalImageUrl,
                proposal.difficulty(),
                proposal.qualityScore(),
                proposal.source(),
                proposal.sourceIndicator(),
                proposal.sourceYear(),
                proposal.factSummary(),
                proposal.evidenceSummary(),
                proposal.duplicateCheckSummary(),
                proposal.searchTerms(),
                proposal.createdAt()
        ));

        executionRepository.saveChangeItem(new AiChangeItem(
                item.id(),
                item.changeSetId(),
                item.runId(),
                item.proposalId(),
                item.itemType(),
                AiChangeItemStatus.APPLIED,
                persistedHint.getId(),
                item.countryId(),
                item.reasonSummary(),
                item.diffSummary(),
                item.originalSnapshot(),
                item.proposedSnapshot(),
                item.createdAt(),
                now
        ));
    }

    private String resolveSourceIndicator(AiGeneratedHintProposal proposal) {
        return firstNonBlank(proposal.sourceIndicator(), AiHintSourceIndicator.forCategory(proposal.category()));
    }

    private void validateGeneratedHintPersistence(Hint persistedHint, AiGeneratedHintProposal proposal, String finalImageUrl) {
        String expectedSourceIndicator = resolveSourceIndicator(proposal);
        if (!Objects.equals(persistedHint.getCountryId(), proposal.countryId())
                || !Objects.equals(persistedHint.getHintText(), proposal.hintText())
                || !Objects.equals(persistedHint.getImageUrl(), finalImageUrl)
                || !Objects.equals(persistedHint.getSourceIndicator(), expectedSourceIndicator)
                || !Boolean.TRUE.equals(persistedHint.getIsActive())
                || persistedHint.getReviewStatus() != HintReviewStatus.REVIEWED) {
            throw new IllegalStateException("Generated hint apply verification failed for proposal " + proposal.id());
        }
    }

    private boolean applyReviewedHint(AiChangeItem item, Instant now, AiApplyBatch batch) {
        Optional<Hint> current = loadCurrentReviewedHint(item);
        if (current.isEmpty()) {
            cancelSupersededItem(item, now);
            return false;
        }
        Hint hint = current.get();
        String originalText = hint.getHintText();
        Long originalImageResourceId = hint.getImageResourceId();
        Map<String, Object> proposed = item.proposedSnapshot() != null ? item.proposedSnapshot() : Map.of();

        String hintText = asString(proposed.getOrDefault("hintText", hint.getHintText()));
        String imageUrl = asString(proposed.getOrDefault("imageUrl", hint.getImageUrl()));
        Boolean isActive = asBoolean(proposed.getOrDefault("isActive", hint.getIsActive()));
        Integer difficulty = asInteger(proposed.get("difficulty"));
        boolean textChanged = !Objects.equals(hint.getHintText(), hintText);

        hint.setHintText(hintText);
        hint.setImageUrl(imageUrl);
        hint.setIsActive(isActive);
        if (difficulty != null) {
            hint.setDifficulty(clampDifficulty(difficulty));
        }
        hint.setContentRevisionHash(deduplicationService.generateRevisionHash(hintText, imageUrl));
        Integer retryAfterDays = asInteger(proposed.get("retryAfterDays"));
        retryAfterDays = retryAfterDays != null ? Math.clamp(retryAfterDays, 30, 365) : null;
        hint.setReviewRetryAfterAt(retryAfterDays != null
                ? now.plusSeconds(retryAfterDays.longValue() * 86400) : null);
        hint.setEditorialTextLocked(Boolean.TRUE.equals(hint.getEditorialTextLocked())
                || textChanged);
        Optional<Hint> persisted = hintRepository.applyAiReviewChange(
                hint, item.runId(), now, reviewedRevision(item), originalText, originalImageResourceId);
        if (persisted.isEmpty()) {
            cancelSupersededItem(item, now);
            return false;
        }
        Hint persistedHint = persisted.get();
        if (textChanged) {
            batch.storeEmbeddingAfterCommit(persistedHint);
        }

        executionRepository.saveChangeItem(new AiChangeItem(
                item.id(),
                item.changeSetId(),
                item.runId(),
                item.proposalId(),
                item.itemType(),
                AiChangeItemStatus.APPLIED,
                item.hintId(),
                item.countryId(),
                item.reasonSummary(),
                item.diffSummary(),
                item.originalSnapshot(),
                item.proposedSnapshot(),
                item.createdAt(),
                now
        ));
        return true;
    }

    private Optional<Hint> loadCurrentReviewedHint(AiChangeItem item) {
        Optional<Hint> current = hintRepository.findById(item.hintId());
        if (current.isEmpty()) {
            return Optional.empty();
        }
        Hint hint = current.get();
        String reviewedRevision = reviewedRevision(item);
        if (reviewedRevision != null && !Objects.equals(reviewedRevision,
                firstNonBlank(hint.getContentRevisionHash(), hint.getContentHash()))) {
            return Optional.empty();
        }
        if (reviewedRevision == null && hasChangedLegacySnapshot(item.originalSnapshot(), hint)) {
            return Optional.empty();
        }
        return Optional.of(hint);
    }

    private String reviewedRevision(AiChangeItem item) {
        return asString(item.originalSnapshot() != null
                ? item.originalSnapshot().get("contentRevisionHash")
                : null);
    }

    private boolean isSupersededReviewItem(AiChangeItem item) {
        if (item.hintId() == null) {
            return false;
        }
        Optional<Hint> current = hintRepository.findById(item.hintId());
        if (current.isEmpty()) {
            return true;
        }
        String revision = reviewedRevision(item);
        if (revision != null) {
            return !Objects.equals(revision,
                    firstNonBlank(current.get().getContentRevisionHash(), current.get().getContentHash()));
        }
        return hasChangedLegacySnapshot(item.originalSnapshot(), current.get());
    }

    private boolean hasChangedLegacySnapshot(Map<String, Object> original, Hint hint) {
        return original != null && ((original.containsKey("hintText")
                && !Objects.equals(asString(original.get("hintText")), hint.getHintText()))
                || (original.containsKey("imageUrl")
                && !Objects.equals(asString(original.get("imageUrl")), hint.getImageUrl())));
    }

    private void cancelSupersededItem(AiChangeItem item, Instant now) {
        executionRepository.saveChangeItem(new AiChangeItem(
                item.id(), item.changeSetId(), item.runId(), item.proposalId(), item.itemType(),
                AiChangeItemStatus.CANCELLED, item.hintId(), item.countryId(), item.reasonSummary(),
                item.diffSummary(), item.originalSnapshot(), item.proposedSnapshot(), item.createdAt(), now));
    }

    private AiChangeSet refreshChangeSet(AiChangeSet changeSet, Instant now) {
        List<AiChangeItem> items = executionRepository.findItemsByChangeSetId(changeSet.id());
        int appliedItems = countByStatus(items, AiChangeItemStatus.APPLIED);
        int rejectedItems = countByStatus(items, AiChangeItemStatus.REJECTED);
        AiChangeSetStatus status = resolveStatus(items, appliedItems, rejectedItems);
        Instant appliedAt = appliedItems > 0 && changeSet.appliedAt() == null ? now : changeSet.appliedAt();
        Instant rejectedAt = status == AiChangeSetStatus.REJECTED && changeSet.rejectedAt() == null
                ? now
                : changeSet.rejectedAt();

        return executionRepository.saveChangeSet(new AiChangeSet(
                changeSet.id(),
                changeSet.runId(),
                changeSet.changeSetType(),
                status,
                changeSet.title(),
                changeSet.summary(),
                items.size(),
                appliedItems,
                rejectedItems,
                changeSet.metadata(),
                changeSet.createdAt(),
                appliedAt,
                rejectedAt
        ));
    }

    /**
     * Mirrors AiChangeSetStatusPolicy in clueterra-api: approving only marks intent, so a set stays
     * a workable draft while any item is still pending or approved.
     */
    private AiChangeSetStatus resolveStatus(List<AiChangeItem> items, int appliedItems, int rejectedItems) {
        if (items.isEmpty() || items.stream().anyMatch(item -> isAwaitingApply(item.status()))) {
            return AiChangeSetStatus.DRAFT;
        }
        if (appliedItems == items.size()) {
            return AiChangeSetStatus.APPLIED;
        }
        if (rejectedItems == items.size()) {
            return AiChangeSetStatus.REJECTED;
        }
        if (countByStatus(items, AiChangeItemStatus.CANCELLED) == items.size()) {
            return AiChangeSetStatus.CANCELLED;
        }
        return AiChangeSetStatus.PARTIALLY_APPLIED;
    }

    private boolean isAwaitingApply(AiChangeItemStatus status) {
        return status == AiChangeItemStatus.PENDING || status == AiChangeItemStatus.APPROVED;
    }

    private int countByStatus(List<AiChangeItem> items, AiChangeItemStatus status) {
        return (int) items.stream().filter(item -> item.status() == status).count();
    }

    private AiChangeSetActionResponse toActionResponse(AiChangeSet changeSet, int appliedNow) {
        return new AiChangeSetActionResponse(
                changeSet.id(),
                changeSet.status().name(),
                changeSet.totalItems(),
                changeSet.appliedItems(),
                changeSet.rejectedItems(),
                appliedNow == 0 ? "Nothing left to apply" : appliedNow + " item(s) applied"
        );
    }

    private String firstNonBlank(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }

    private Integer clampDifficulty(Integer difficulty) {
        if (difficulty == null) {
            return 50;
        }
        return Math.clamp(difficulty, 1, 100);
    }

    private BigDecimal toBigDecimal(Double value) {
        if (value == null) {
            return null;
        }
        double normalized = value > 1.0 ? value / 100.0 : value;
        return BigDecimal.valueOf(normalized);
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null ? Boolean.parseBoolean(String.valueOf(value)) : null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
