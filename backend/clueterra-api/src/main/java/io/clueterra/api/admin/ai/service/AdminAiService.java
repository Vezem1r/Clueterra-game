package io.clueterra.api.admin.ai.service;

import io.clueterra.api.admin.ai.dto.AiChangeItemDto;
import io.clueterra.api.admin.ai.dto.AiChangeSetActionResponse;
import io.clueterra.api.admin.ai.dto.AiChangeSetDetailResponse;
import io.clueterra.api.admin.ai.dto.AiChangeSetDto;
import io.clueterra.api.admin.ai.dto.AiRunDetailResponse;
import io.clueterra.api.admin.ai.dto.AiRunDto;
import io.clueterra.api.admin.ai.repository.AdminAiJdbcRepository;
import io.clueterra.api.common.dto.PageResponse;
import io.clueterra.api.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAiService {

    private static final String PROPOSAL_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String MESSAGE_ITEM_APPROVED = "Item marked for apply";
    private static final String MESSAGE_ITEM_REJECTED = "Item marked for rejection";
    private static final List<String> ALL_CHANGE_ITEM_TYPES = List.of("HINT_UPDATE", "HINT_DISABLE", "HINT_CREATE", "IMAGE_REPLACE", "HINT_ESCALATE");
    private static final Set<String> CHANGE_ITEM_TYPES = Set.copyOf(ALL_CHANGE_ITEM_TYPES);

    private final AdminAiJdbcRepository repository;
    private final Clock clock;

    public PageResponse<AiRunDto> getRecentRuns(int page, int size) {
        return repository.findRecentRuns(page, size);
    }

    public AiRunDetailResponse getRunDetail(Long runId) {
        AiRunDto run = repository.findRunById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("AI run not found: " + runId));
        AiChangeSetDto changeSet = repository.findChangeSetByRunId(runId).orElse(null);
        List<AiChangeItemDto> changeItems = changeSet != null
                ? repository.findItemsByChangeSetId(changeSet.id())
                : List.of();
        return new AiRunDetailResponse(run, repository.findStagesByRunId(runId), changeSet, changeItems);
    }

    public PageResponse<AiChangeSetDto> getRecentChangeSets(int page, int size) {
        return repository.findRecentChangeSets(page, size);
    }

    public AiChangeSetDetailResponse getChangeSetDetail(Long changeSetId) {
        AiChangeSetDto changeSet = repository.findChangeSetById(changeSetId)
                .orElseThrow(() -> new ResourceNotFoundException("AI change set not found: " + changeSetId));
        return new AiChangeSetDetailResponse(
                changeSet,
                repository.findItemsByChangeSetId(changeSetId),
                repository.findProposalsByChangeSetId(changeSetId));
    }

    @Transactional
    public AiChangeSetActionResponse approveItem(Long changeSetId, Long itemId) {
        AiChangeSetDto changeSet = loadChangeSet(changeSetId);
        AiChangeItemDto item = loadMarkableItem(changeSetId, itemId);
        if (AiChangeSetStatusPolicy.isTerminal(item.status())) {
            return refreshAndRespond(changeSet, "Item is already finalized");
        }

        repository.updateItemStatus(item.id(), AiChangeSetStatusPolicy.ITEM_APPROVED, Instant.now(clock));
        if (item.proposalId() != null) {
            // Clears a previous rejection so re-approving leaves the proposal consistent with the item.
            repository.updateProposalStatus(item.proposalId(), PROPOSAL_PENDING_APPROVAL);
        } else if (item.hintId() != null && AiChangeSetStatusPolicy.ITEM_REJECTED.equals(item.status())) {
            repository.markHintReviewActionPending(item.hintId());
        }
        return refreshAndRespond(changeSet, MESSAGE_ITEM_APPROVED);
    }

    @Transactional
    public AiChangeSetActionResponse rejectItem(Long changeSetId, Long itemId) {
        AiChangeSetDto changeSet = loadChangeSet(changeSetId);
        AiChangeItemDto item = loadMarkableItem(changeSetId, itemId);
        if (AiChangeSetStatusPolicy.isTerminal(item.status())) {
            return refreshAndRespond(changeSet, "Item is already finalized");
        }
        Instant now = Instant.now(clock);

        rejectItemInternal(item, now);
        return refreshAndRespond(changeSet, MESSAGE_ITEM_REJECTED);
    }

    @Transactional
    public AiChangeSetActionResponse rejectAll(Long changeSetId) {
        return decideAll(changeSetId, ALL_CHANGE_ITEM_TYPES, AiChangeSetStatusPolicy.ITEM_REJECTED);
    }

    @Transactional
    public AiChangeSetActionResponse rejectAll(Long changeSetId, List<String> itemTypes) {
        return decideAll(changeSetId, itemTypes, AiChangeSetStatusPolicy.ITEM_REJECTED);
    }

    @Transactional
    public AiChangeSetActionResponse approveAll(Long changeSetId, List<String> itemTypes) {
        return decideAll(changeSetId, itemTypes, AiChangeSetStatusPolicy.ITEM_APPROVED);
    }

    private AiChangeSetActionResponse decideAll(Long changeSetId, List<String> requestedTypes, String decision) {
        loadChangeSet(changeSetId);
        List<String> itemTypes = requestedTypes == null || requestedTypes.isEmpty()
                ? ALL_CHANGE_ITEM_TYPES
                : requestedTypes.stream().distinct().toList();
        if (!CHANGE_ITEM_TYPES.containsAll(itemTypes)) {
            throw new IllegalArgumentException("Unsupported AI change item type");
        }
        return repository.decideChangeItems(changeSetId, itemTypes, decision);
    }

    private void rejectItemInternal(AiChangeItemDto item, Instant now) {
        repository.updateItemStatus(item.id(), AiChangeSetStatusPolicy.ITEM_REJECTED, now);
        if (item.proposalId() != null) {
            repository.updateProposalStatus(item.proposalId(), AiChangeSetStatusPolicy.ITEM_REJECTED);
        }
        if (item.hintId() != null && item.proposalId() == null) {
            repository.markHintForReviewRetry(item.hintId(), item.runId(), now);
        }
    }

    private AiChangeSetDto loadChangeSet(Long changeSetId) {
        return repository.findChangeSetByIdForUpdate(changeSetId)
                .orElseThrow(() -> new ResourceNotFoundException("AI change set not found: " + changeSetId));
    }

    private AiChangeItemDto loadMarkableItem(Long changeSetId, Long itemId) {
        AiChangeItemDto item = repository.findItemById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("AI change item not found: " + itemId));
        if (!Objects.equals(item.changeSetId(), changeSetId)) {
            throw new IllegalArgumentException("AI change item does not belong to change set: " + itemId);
        }
        if (item.proposedSnapshot() != null && Boolean.TRUE.equals(item.proposedSnapshot().get("autoRejected"))) {
            throw new IllegalStateException("AI change item was rejected by the answer-leak safety check: " + itemId);
        }
        return item;
    }

    private AiChangeSetActionResponse refreshAndRespond(AiChangeSetDto changeSet, String message) {
        List<AiChangeItemDto> items = repository.findItemsByChangeSetId(changeSet.id());
        int appliedCount = AiChangeSetStatusPolicy.count(items, AiChangeSetStatusPolicy.ITEM_APPLIED);
        int rejectedCount = AiChangeSetStatusPolicy.count(items, AiChangeSetStatusPolicy.ITEM_REJECTED);
        String status = AiChangeSetStatusPolicy.resolveSetStatus(items);
        Instant rejectedAt = AiChangeSetStatusPolicy.SET_REJECTED.equals(status) && changeSet.rejectedAt() == null
                ? Instant.now(clock)
                : changeSet.rejectedAt();

        repository.updateChangeSet(changeSet.id(), status, items.size(),
                appliedCount, rejectedCount, changeSet.appliedAt(), rejectedAt);

        return new AiChangeSetActionResponse(
                String.valueOf(changeSet.id()), status, items.size(),
                appliedCount, rejectedCount, message);
    }
}
