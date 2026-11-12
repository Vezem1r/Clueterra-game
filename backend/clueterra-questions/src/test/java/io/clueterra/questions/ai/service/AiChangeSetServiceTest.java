package io.clueterra.questions.ai.service;

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
import io.clueterra.questions.ai.service.change.AiChangeSetService;
import io.clueterra.questions.ai.service.generation.AiWikimediaAttributionService;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.repository.HintSaveResult;
import io.clueterra.questions.repository.HintUpsertAction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.clueterra.questions.service.deduplication.DeduplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChangeSetServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-03T12:00:00Z");

    @Mock
    private AiExecutionRepository executionRepository;

    @Mock
    private HintRepository hintRepository;

    @Mock
    private DeduplicationService deduplicationService;

    @Mock
    private AiWikimediaAttributionService wikimediaAttributionService;

    @Test
    void applyGeneratedHintPersistsActualHintIdOnAppliedChangeItem() {
        AiChangeSetService service = new AiChangeSetService(
                executionRepository,
                hintRepository,
                deduplicationService,
                wikimediaAttributionService,
                Optional.empty(),
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        long changeSetId = 100L;
        long runId = 200L;
        long proposalId = 300L;
        long countryId = 400L;
        long persistedHintId = 500L;

        AiChangeSet changeSet = new AiChangeSet(
                changeSetId,
                runId,
                AiChangeSetType.GENERATION,
                AiChangeSetStatus.DRAFT,
                "Generated hints",
                "Apply generated hints",
                1,
                0,
                0,
                Map.of(),
                NOW.minusSeconds(60),
                null,
                null
        );
        AiChangeItem pendingItem = new AiChangeItem(
                600L,
                changeSetId,
                runId,
                proposalId,
                AiChangeItemType.HINT_CREATE,
                AiChangeItemStatus.PENDING,
                null,
                countryId,
                "Create hint",
                "New AI-generated hint",
                null,
                Map.of("hintText", "A new clue"),
                NOW.minusSeconds(30),
                null
        );
        AiChangeItem appliedItem = new AiChangeItem(
                pendingItem.id(),
                pendingItem.changeSetId(),
                pendingItem.runId(),
                pendingItem.proposalId(),
                pendingItem.itemType(),
                AiChangeItemStatus.APPLIED,
                persistedHintId,
                pendingItem.countryId(),
                pendingItem.reasonSummary(),
                pendingItem.diffSummary(),
                pendingItem.originalSnapshot(),
                pendingItem.proposedSnapshot(),
                pendingItem.createdAt(),
                NOW
        );
        AiGeneratedHintProposal proposal = new AiGeneratedHintProposal(
                proposalId,
                runId,
                700L,
                changeSetId,
                countryId,
                AiProposalStatus.PENDING_APPROVAL,
                Category.CULTURE.name(),
                HintType.CULTURAL.name(),
                "A new clue",
                null,
                null,
                44,
                0.91,
                DataSource.MANUAL.name(),
                "AI_GENERATED",
                2026,
                "Fact summary",
                "Evidence summary",
                "Duplicate summary",
                List.of("new clue"),
                NOW.minusSeconds(90)
        );
        Hint persistedHint = Hint.builder()
                .id(persistedHintId)
                .countryId(countryId)
                .category(Category.CULTURE)
                .hintType(HintType.CULTURAL)
                .hintText("A new clue")
                .difficulty(44)
                .qualityScore(BigDecimal.valueOf(0.91))
                .source(DataSource.MANUAL)
                .sourceIndicator("AI_GENERATED")
                .contentHash("semantic-hash")
                .contentRevisionHash("revision-hash")
                .ambiguityCount(1)
                .isActive(true)
                .reviewStatus(HintReviewStatus.REVIEWED)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId)).thenReturn(Optional.of(changeSet));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId)).thenReturn(List.of(pendingItem));
        when(executionRepository.findItemsByChangeSetId(changeSetId)).thenReturn(List.of(appliedItem));
        when(executionRepository.findProposalById(proposalId)).thenReturn(Optional.of(proposal));
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.saveGeneratedHintProposal(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.saveChangeItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deduplicationService.generateSemanticHash("A new clue")).thenReturn("semantic-hash");
        when(deduplicationService.generateRevisionHash("A new clue", null)).thenReturn("revision-hash");
        when(hintRepository.save(any(Hint.class))).thenReturn(new HintSaveResult(persistedHint, HintUpsertAction.INSERTED));

        AiChangeSetActionResponse response = service.apply(changeSetId);

        ArgumentCaptor<AiChangeItem> changeItemCaptor = ArgumentCaptor.forClass(AiChangeItem.class);
        verify(executionRepository).saveChangeItem(changeItemCaptor.capture());

        AiChangeItem savedAppliedItem = changeItemCaptor.getValue();
        assertThat(savedAppliedItem.status()).isEqualTo(AiChangeItemStatus.APPLIED);
        assertThat(savedAppliedItem.hintId()).isEqualTo(persistedHintId);
        assertThat(savedAppliedItem.proposalId()).isEqualTo(proposalId);
        assertThat(response.changeSetId()).isEqualTo(changeSetId);
        assertThat(response.status()).isEqualTo(AiChangeSetStatus.APPLIED.name());
        assertThat(response.appliedItems()).isEqualTo(1);
    }

    @Test
    void applyConsumesApprovedItemsBecauseApprovingOnlyMarkedThemForApply() {
        AiChangeSetService service = newService();
        long changeSetId = 101L;
        long runId = 201L;
        long hintId = 501L;

        AiChangeItem approvedItem = escalationItem(601L, changeSetId, runId, hintId, AiChangeItemStatus.APPROVED);
        AiChangeItem appliedItem = escalationItem(601L, changeSetId, runId, hintId, AiChangeItemStatus.APPLIED);

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId)).thenReturn(List.of(approvedItem));
        when(executionRepository.findItemsByChangeSetId(changeSetId))
                .thenReturn(List.of(appliedItem));
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.saveChangeItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hintRepository.findById(hintId)).thenReturn(Optional.of(
                Hint.builder().id(hintId).hintText("An existing clue").build()));

        AiChangeSetActionResponse response = service.apply(changeSetId);

        verify(hintRepository).markReviewed(hintId, runId, NOW);
        assertThat(response.status()).isEqualTo(AiChangeSetStatus.APPLIED.name());
        assertThat(response.appliedItems()).isEqualTo(1);
    }

    @Test
    void autoApplyConsumesAllTextUpdatesEvenWhenEmbeddingCheckFlagsOne() {
        AiChangeSetService service = newService();
        long changeSetId = 105L;
        long runId = 205L;
        long safeHintId = 505L;
        long flaggedHintId = 506L;
        long disableHintId = 507L;

        AiChangeItem safeUpdate = new AiChangeItem(
                605L,
                changeSetId,
                runId,
                null,
                AiChangeItemType.HINT_UPDATE,
                AiChangeItemStatus.PENDING,
                safeHintId,
                401L,
                "Safe anchored rewrite",
                "hintText updated",
                Map.of("hintText", "An old clue", "contentRevisionHash", "old-revision"),
                Map.of("hintText", "A clearer anchored clue", "difficulty", 62, "autoApplyEligible", true),
                NOW.minusSeconds(30),
                null
        );
        AiChangeItem flaggedUpdate = new AiChangeItem(
                606L,
                changeSetId,
                runId,
                null,
                AiChangeItemType.HINT_UPDATE,
                AiChangeItemStatus.PENDING,
                flaggedHintId,
                401L,
                "Rewrite with an advisory validation warning",
                "hintText updated",
                Map.of("hintText", "Another old clue", "contentRevisionHash", "flagged-revision"),
                Map.of("hintText", "An expanded clue with a new anchor", "autoApplyEligible", false),
                NOW.minusSeconds(30),
                null
        );
        AiChangeItem disable = new AiChangeItem(
                607L,
                changeSetId,
                runId,
                null,
                AiChangeItemType.HINT_DISABLE,
                AiChangeItemStatus.PENDING,
                disableHintId,
                401L,
                "Disable suggestion",
                "isActive: true -> false",
                Map.of("hintText", "A third clue", "contentRevisionHash", "disable-revision"),
                Map.of("isActive", false, "autoApplyEligible", true),
                NOW.minusSeconds(30),
                null
        );
        AiChangeItem appliedSafeUpdate = new AiChangeItem(
                safeUpdate.id(),
                safeUpdate.changeSetId(),
                safeUpdate.runId(),
                safeUpdate.proposalId(),
                safeUpdate.itemType(),
                AiChangeItemStatus.APPLIED,
                safeUpdate.hintId(),
                safeUpdate.countryId(),
                safeUpdate.reasonSummary(),
                safeUpdate.diffSummary(),
                safeUpdate.originalSnapshot(),
                safeUpdate.proposedSnapshot(),
                safeUpdate.createdAt(),
                NOW
        );
        AiChangeItem appliedFlaggedUpdate = new AiChangeItem(
                flaggedUpdate.id(), flaggedUpdate.changeSetId(), flaggedUpdate.runId(),
                flaggedUpdate.proposalId(), flaggedUpdate.itemType(), AiChangeItemStatus.APPLIED,
                flaggedUpdate.hintId(), flaggedUpdate.countryId(), flaggedUpdate.reasonSummary(),
                flaggedUpdate.diffSummary(), flaggedUpdate.originalSnapshot(), flaggedUpdate.proposedSnapshot(),
                flaggedUpdate.createdAt(), NOW);
        Hint safeHint = Hint.builder()
                .id(safeHintId)
                .countryId(401L)
                .category(Category.CULTURE)
                .hintType(HintType.CULTURAL)
                .hintText("An old clue")
                .difficulty(55)
                .qualityScore(BigDecimal.valueOf(0.8))
                .source(DataSource.MANUAL)
                .sourceIndicator("MANUAL_CLUE")
                .contentHash("old-hash")
                .contentRevisionHash("old-revision")
                .ambiguityCount(1)
                .isActive(true)
                .reviewStatus(HintReviewStatus.ACTION_PENDING)
                .createdAt(NOW.minusSeconds(120))
                .updatedAt(NOW.minusSeconds(60))
                .build();
        Hint flaggedHint = Hint.builder()
                .id(flaggedHintId)
                .countryId(401L)
                .category(Category.CULTURE)
                .hintType(HintType.CULTURAL)
                .hintText("Another old clue")
                .difficulty(55)
                .source(DataSource.MANUAL)
                .contentRevisionHash("flagged-revision")
                .isActive(true)
                .reviewStatus(HintReviewStatus.ACTION_PENDING)
                .build();

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId))
                .thenReturn(List.of(safeUpdate, flaggedUpdate, disable));
        when(executionRepository.findItemsByChangeSetId(changeSetId))
                .thenReturn(List.of(appliedSafeUpdate, appliedFlaggedUpdate, disable));
        when(executionRepository.saveChangeItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hintRepository.findById(safeHintId)).thenReturn(Optional.of(safeHint));
        when(hintRepository.findById(flaggedHintId)).thenReturn(Optional.of(flaggedHint));
        when(deduplicationService.generateRevisionHash("A clearer anchored clue", null))
                .thenReturn("new-revision");
        when(deduplicationService.generateRevisionHash("An expanded clue with a new anchor", null))
                .thenReturn("flagged-new-revision");
        when(hintRepository.applyAiReviewChange(any(Hint.class), eq(runId), eq(NOW),
                nullable(String.class), nullable(String.class), nullable(Long.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        AiChangeSetActionResponse response = service.applySafeReviewUpdates(changeSetId);

        verify(hintRepository, times(2)).findById(safeHintId);
        verify(hintRepository, times(2)).findById(flaggedHintId);
        verify(hintRepository, never()).findById(disableHintId);
        verify(hintRepository, times(2)).applyAiReviewChange(any(Hint.class), eq(runId), eq(NOW),
                nullable(String.class), nullable(String.class), nullable(Long.class));
        assertThat(safeHint.getHintText()).isEqualTo("A clearer anchored clue");
        assertThat(safeHint.getDifficulty()).isEqualTo(62);
        assertThat(safeHint.getContentRevisionHash()).isEqualTo("new-revision");
        assertThat(safeHint.getEditorialTextLocked()).isTrue();
        assertThat(safeHint.getReviewRetryAfterAt()).isNull();
        assertThat(flaggedHint.getHintText()).isEqualTo("An expanded clue with a new anchor");
        assertThat(flaggedHint.getEditorialTextLocked()).isTrue();
        assertThat(response.appliedItems()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(AiChangeSetStatus.DRAFT.name());
    }

    @Test
    void autoApplyDoesNotTurnTextUpdatesIntoImageChangesOrAnswerLeaks() {
        AiChangeSetService service = newService();
        long changeSetId = 108L;
        long runId = 208L;
        AiChangeItem imageChange = new AiChangeItem(
                608L, changeSetId, runId, null, AiChangeItemType.HINT_UPDATE,
                AiChangeItemStatus.PENDING, 508L, 401L, "Image replacement", "imageUrl updated",
                Map.of("hintText", "An old clue", "imageUrl", "old.jpg", "isActive", true),
                Map.of("hintText", "A new clue", "imageUrl", "new.jpg", "isActive", true,
                        "autoApplyEligible", false), NOW.minusSeconds(30), null);
        AiChangeItem answerLeak = new AiChangeItem(
                609L, changeSetId, runId, null, AiChangeItemType.HINT_UPDATE,
                AiChangeItemStatus.PENDING, 509L, 401L, "Answer leak", "hintText updated",
                Map.of("hintText", "A long river runs here.", "countryName", "Germany", "isActive", true),
                Map.of("hintText", "A long river runs through Germany.", "isActive", true,
                        "autoApplyEligible", false), NOW.minusSeconds(30), null);
        AiChangeItem disableDisguisedAsEdit = new AiChangeItem(
                610L, changeSetId, runId, null, AiChangeItemType.HINT_UPDATE,
                AiChangeItemStatus.PENDING, 510L, 401L, "Status change", "isActive updated",
                Map.of("hintText", "An old clue", "isActive", true),
                Map.of("hintText", "A new clue", "isActive", false,
                        "autoApplyEligible", false), NOW.minusSeconds(30), null);
        List<AiChangeItem> items = List.of(imageChange, answerLeak, disableDisguisedAsEdit);

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId)).thenReturn(items);
        when(executionRepository.findItemsByChangeSetId(changeSetId))
                .thenReturn(items);
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AiChangeSetActionResponse response = service.applySafeReviewUpdates(changeSetId);

        verifyNoInteractions(hintRepository);
        assertThat(response.appliedItems()).isZero();
        assertThat(response.status()).isEqualTo(AiChangeSetStatus.DRAFT.name());
    }

    @Test
    void approvedTemporalEditSchedulesReviewAndProtectsItsText() {
        AiChangeSetService service = newService();
        long changeSetId = 106L;
        long runId = 206L;
        long hintId = 506L;
        AiChangeItem approvedItem = new AiChangeItem(
                606L, changeSetId, runId, null, AiChangeItemType.HINT_UPDATE,
                AiChangeItemStatus.APPROVED, hintId, 401L, "Current leader",
                "hintText updated; retry after 90 days",
                Map.of("hintText", "The current leader is named here.", "contentRevisionHash", "old-revision"),
                Map.of("hintText", "The current leader has held office since a dated election.",
                        "retryAfterDays", 90),
                NOW.minusSeconds(30), null);
        AiChangeItem appliedItem = new AiChangeItem(
                approvedItem.id(), changeSetId, runId, null, AiChangeItemType.HINT_UPDATE,
                AiChangeItemStatus.APPLIED, hintId, 401L, approvedItem.reasonSummary(),
                approvedItem.diffSummary(), approvedItem.originalSnapshot(), approvedItem.proposedSnapshot(),
                approvedItem.createdAt(), NOW);
        Hint hint = Hint.builder()
                .id(hintId)
                .countryId(401L)
                .category(Category.POLITICS)
                .hintType(HintType.CULTURAL)
                .hintText("The current leader is named here.")
                .difficulty(55)
                .source(DataSource.CIA_FACTBOOK)
                .contentRevisionHash("old-revision")
                .isActive(true)
                .reviewStatus(HintReviewStatus.ACTION_PENDING)
                .build();

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId)).thenReturn(List.of(approvedItem));
        when(executionRepository.findItemsByChangeSetId(changeSetId))
                .thenReturn(List.of(appliedItem));
        when(executionRepository.saveChangeItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hintRepository.findById(hintId)).thenReturn(Optional.of(hint));
        when(deduplicationService.generateRevisionHash(
                "The current leader has held office since a dated election.", null)).thenReturn("new-revision");
        when(hintRepository.applyAiReviewChange(any(Hint.class), eq(runId), eq(NOW),
                nullable(String.class), nullable(String.class), nullable(Long.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        AiChangeSetActionResponse response = service.apply(changeSetId);

        assertThat(hint.getEditorialTextLocked()).isTrue();
        assertThat(hint.getReviewRetryAfterAt()).isEqualTo(NOW.plusSeconds(90L * 86400));
        assertThat(hint.getContentRevisionHash()).isEqualTo("new-revision");
        assertThat(response.appliedItems()).isEqualTo(1);
    }

    @Test
    void applyAfterManualTriageAppliesApprovedItemsAndLeavesRejectedOnesUntouched() {
        AiChangeSetService service = newService();
        long changeSetId = 103L;
        long runId = 203L;
        long approvedHintId = 503L;
        long rejectedHintId = 504L;

        AiChangeItem approvedItem = escalationItem(603L, changeSetId, runId, approvedHintId, AiChangeItemStatus.APPROVED);
        AiChangeItem rejectedItem = escalationItem(604L, changeSetId, runId, rejectedHintId, AiChangeItemStatus.REJECTED);
        AiChangeItem appliedItem = escalationItem(603L, changeSetId, runId, approvedHintId, AiChangeItemStatus.APPLIED);

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId))
                .thenReturn(List.of(approvedItem, rejectedItem));
        when(executionRepository.findItemsByChangeSetId(changeSetId))
                .thenReturn(List.of(appliedItem, rejectedItem));
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.saveChangeItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hintRepository.findById(approvedHintId)).thenReturn(Optional.of(
                Hint.builder().id(approvedHintId).hintText("An existing clue").build()));

        AiChangeSetActionResponse response = service.apply(changeSetId);

        verify(hintRepository).markReviewed(approvedHintId, runId, NOW);
        verify(hintRepository, never()).markReviewed(rejectedHintId, runId, NOW);
        assertThat(response.status()).isEqualTo(AiChangeSetStatus.PARTIALLY_APPLIED.name());
        assertThat(response.appliedItems()).isEqualTo(1);
        assertThat(response.rejectedItems()).isEqualTo(1);
    }

    @Test
    void applyOnAFullyRejectedSetAppliesNothingAndReportsRejected() {
        AiChangeSetService service = newService();
        long changeSetId = 102L;
        long runId = 202L;

        AiChangeItem rejectedItem = escalationItem(602L, changeSetId, runId, 502L, AiChangeItemStatus.REJECTED);

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId)).thenReturn(List.of(rejectedItem));
        when(executionRepository.findItemsByChangeSetId(changeSetId)).thenReturn(List.of(rejectedItem));
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AiChangeSetActionResponse response = service.apply(changeSetId);

        verify(executionRepository, never()).saveChangeItem(any());
        assertThat(response.status()).isEqualTo(AiChangeSetStatus.REJECTED.name());
        assertThat(response.appliedItems()).isZero();
        assertThat(response.rejectedItems()).isEqualTo(1);
    }

    @Test
    void staleApprovedEscalationDoesNotBlockAnotherApprovedEdit() {
        AiChangeSetService service = newService();
        long changeSetId = 110L;
        long runId = 210L;
        long staleHintId = 510L;
        long currentHintId = 511L;
        AiChangeItem stale = new AiChangeItem(
                610L, changeSetId, runId, null, AiChangeItemType.HINT_ESCALATE,
                AiChangeItemStatus.APPROVED, staleHintId, 401L, "Old escalation", null,
                Map.of("hintText", "Old text", "contentRevisionHash", "old-revision"), null,
                NOW.minusSeconds(30), null);
        AiChangeItem current = new AiChangeItem(
                611L, changeSetId, runId, null, AiChangeItemType.HINT_ESCALATE,
                AiChangeItemStatus.APPROVED, currentHintId, 401L, "Current escalation", null,
                Map.of("hintText", "Current text", "contentRevisionHash", "current-revision"), null,
                NOW.minusSeconds(30), null);
        AiChangeItem cancelled = new AiChangeItem(
                stale.id(), changeSetId, runId, null, stale.itemType(), AiChangeItemStatus.CANCELLED,
                staleHintId, 401L, stale.reasonSummary(), null, stale.originalSnapshot(), null,
                stale.createdAt(), NOW);
        AiChangeItem applied = new AiChangeItem(
                current.id(), changeSetId, runId, null, current.itemType(), AiChangeItemStatus.APPLIED,
                currentHintId, 401L, current.reasonSummary(), null, current.originalSnapshot(), null,
                current.createdAt(), NOW);

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId))
                .thenReturn(List.of(stale, current));
        when(executionRepository.findItemsByChangeSetId(changeSetId))
                .thenReturn(List.of(cancelled, applied));
        when(executionRepository.saveChangeItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hintRepository.findById(staleHintId)).thenReturn(Optional.of(
                Hint.builder().id(staleHintId).hintText("New text")
                        .contentRevisionHash("new-revision").build()));
        when(hintRepository.findById(currentHintId)).thenReturn(Optional.of(
                Hint.builder().id(currentHintId).hintText("Current text")
                        .contentRevisionHash("current-revision").build()));

        AiChangeSetActionResponse response = service.apply(changeSetId);

        ArgumentCaptor<AiChangeItem> saved = ArgumentCaptor.forClass(AiChangeItem.class);
        verify(executionRepository, times(2)).saveChangeItem(saved.capture());
        assertThat(saved.getAllValues()).extracting(AiChangeItem::status)
                .containsExactly(AiChangeItemStatus.CANCELLED, AiChangeItemStatus.APPLIED);
        verify(hintRepository, never()).markReviewed(eq(staleHintId), eq(runId), eq(NOW));
        verify(hintRepository).markReviewed(currentHintId, runId, NOW);
        assertThat(response.appliedItems()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(AiChangeSetStatus.PARTIALLY_APPLIED.name());
    }

    @Test
    void legacyPendingReviewItemWithChangedTextIsCancelledWhenApplyingSet() {
        AiChangeSetService service = newService();
        long changeSetId = 111L;
        long runId = 211L;
        long hintId = 512L;
        AiChangeItem pending = new AiChangeItem(
                612L, changeSetId, runId, null, AiChangeItemType.HINT_UPDATE,
                AiChangeItemStatus.PENDING, hintId, 401L, "Legacy edit", null,
                Map.of("hintText", "Original clue"), Map.of("hintText", "Proposed clue"),
                NOW.minusSeconds(30), null);
        AiChangeItem cancelled = new AiChangeItem(
                pending.id(), changeSetId, runId, null, pending.itemType(), AiChangeItemStatus.CANCELLED,
                hintId, 401L, pending.reasonSummary(), null, pending.originalSnapshot(),
                pending.proposedSnapshot(), pending.createdAt(), NOW);

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId)).thenReturn(List.of(pending));
        when(executionRepository.findItemsByChangeSetId(changeSetId)).thenReturn(List.of(cancelled));
        when(executionRepository.saveChangeItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hintRepository.findById(hintId)).thenReturn(Optional.of(
                Hint.builder().id(hintId).hintText("Changed since review").build()));

        AiChangeSetActionResponse response = service.apply(changeSetId);

        ArgumentCaptor<AiChangeItem> saved = ArgumentCaptor.forClass(AiChangeItem.class);
        verify(executionRepository).saveChangeItem(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(AiChangeItemStatus.CANCELLED);
        verify(hintRepository, never()).applyAiReviewChange(any(Hint.class), eq(runId), eq(NOW),
                nullable(String.class), nullable(String.class), nullable(Long.class));
        assertThat(response.appliedItems()).isZero();
        assertThat(response.status()).isEqualTo(AiChangeSetStatus.CANCELLED.name());
    }

    @Test
    void reviewEditChangedAfterInitialCheckIsCancelledWithoutBlockingApply() {
        AiChangeSetService service = newService();
        long changeSetId = 112L;
        long runId = 212L;
        long hintId = 513L;
        AiChangeItem approved = new AiChangeItem(
                613L, changeSetId, runId, null, AiChangeItemType.HINT_UPDATE,
                AiChangeItemStatus.APPROVED, hintId, 401L, "Edit", "Text updated",
                Map.of("hintText", "Original clue", "contentRevisionHash", "original-revision"),
                Map.of("hintText", "AI rewrite"), NOW.minusSeconds(30), null);
        AiChangeItem cancelled = new AiChangeItem(
                approved.id(), changeSetId, runId, null, approved.itemType(), AiChangeItemStatus.CANCELLED,
                hintId, 401L, approved.reasonSummary(), approved.diffSummary(), approved.originalSnapshot(),
                approved.proposedSnapshot(), approved.createdAt(), NOW);
        Hint original = Hint.builder().id(hintId).hintText("Original clue")
                .contentRevisionHash("original-revision").build();
        Hint changed = Hint.builder().id(hintId).hintText("Newer clue")
                .contentRevisionHash("new-revision").build();

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId)).thenReturn(List.of(approved));
        when(executionRepository.findItemsByChangeSetId(changeSetId)).thenReturn(List.of(cancelled));
        when(executionRepository.saveChangeItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hintRepository.findById(hintId)).thenReturn(Optional.of(original), Optional.of(changed));

        AiChangeSetActionResponse response = service.apply(changeSetId);

        ArgumentCaptor<AiChangeItem> saved = ArgumentCaptor.forClass(AiChangeItem.class);
        verify(executionRepository).saveChangeItem(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(AiChangeItemStatus.CANCELLED);
        verify(hintRepository, never()).applyAiReviewChange(any(Hint.class), eq(runId), eq(NOW),
                nullable(String.class), nullable(String.class), nullable(Long.class));
        assertThat(response.appliedItems()).isZero();
        assertThat(response.status()).isEqualTo(AiChangeSetStatus.CANCELLED.name());
    }

    @Test
    void reviewEditChangedDuringSqlUpdateIsCancelledInsteadOfReturningConflict() {
        AiChangeSetService service = newService();
        long changeSetId = 113L;
        long runId = 213L;
        long hintId = 514L;
        AiChangeItem approved = new AiChangeItem(
                614L, changeSetId, runId, null, AiChangeItemType.HINT_UPDATE,
                AiChangeItemStatus.APPROVED, hintId, 401L, "Edit", "Text updated",
                Map.of("hintText", "Original clue", "contentRevisionHash", "original-revision"),
                Map.of("hintText", "AI rewrite"), NOW.minusSeconds(30), null);
        AiChangeItem cancelled = new AiChangeItem(
                approved.id(), changeSetId, runId, null, approved.itemType(), AiChangeItemStatus.CANCELLED,
                hintId, 401L, approved.reasonSummary(), approved.diffSummary(), approved.originalSnapshot(),
                approved.proposedSnapshot(), approved.createdAt(), NOW);
        Hint original = Hint.builder().id(hintId).hintText("Original clue")
                .contentRevisionHash("original-revision").isActive(true).build();

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId)).thenReturn(List.of(approved));
        when(executionRepository.findItemsByChangeSetId(changeSetId)).thenReturn(List.of(cancelled));
        when(executionRepository.saveChangeItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.saveChangeSet(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hintRepository.findById(hintId)).thenReturn(Optional.of(original));
        when(deduplicationService.generateRevisionHash("AI rewrite", null)).thenReturn("rewritten-revision");
        when(hintRepository.applyAiReviewChange(any(Hint.class), eq(runId), eq(NOW),
                eq("original-revision"), eq("Original clue"), nullable(Long.class)))
                .thenReturn(Optional.empty());

        AiChangeSetActionResponse response = service.apply(changeSetId);

        ArgumentCaptor<AiChangeItem> saved = ArgumentCaptor.forClass(AiChangeItem.class);
        verify(executionRepository).saveChangeItem(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(AiChangeItemStatus.CANCELLED);
        assertThat(response.appliedItems()).isZero();
        assertThat(response.status()).isEqualTo(AiChangeSetStatus.CANCELLED.name());
    }

    @Test
    void applyGeneratedImageFailsWhenWikimediaAttributionIsNoLongerAvailable() {
        AiChangeSetService service = newService();
        long changeSetId = 104L;
        long runId = 204L;
        long proposalId = 304L;
        String sourceUrl = "https://upload.wikimedia.org/example.jpg";

        AiChangeItem pendingItem = new AiChangeItem(
                604L,
                changeSetId,
                runId,
                proposalId,
                AiChangeItemType.HINT_CREATE,
                AiChangeItemStatus.APPROVED,
                null,
                404L,
                "Create image hint",
                "Visually checked image",
                null,
                Map.of("imageValidated", true, "imageKind", "LANDMARK"),
                NOW.minusSeconds(30),
                null
        );
        AiGeneratedHintProposal proposal = new AiGeneratedHintProposal(
                proposalId,
                runId,
                704L,
                changeSetId,
                404L,
                AiProposalStatus.PENDING_APPROVAL,
                Category.LANDMARKS.name(),
                HintType.IMAGE_FACT.name(),
                "A carved stone tower rises above the surrounding forest.",
                sourceUrl,
                "https://cdn.example.com/hint-images/ai/example.jpg",
                55,
                0.90,
                DataSource.WIKIMEDIA_COMMONS.name(),
                "AI_LANDMARK",
                null,
                "Landmark fact",
                "Commons metadata",
                "No duplicate",
                List.of("stone tower"),
                NOW.minusSeconds(90)
        );

        when(executionRepository.findChangeSetByIdForUpdate(changeSetId))
                .thenReturn(Optional.of(draftChangeSet(changeSetId, runId)));
        when(executionRepository.findItemsByChangeSetIdForUpdate(changeSetId)).thenReturn(List.of(pendingItem));
        when(executionRepository.findApplicableProposalIds(changeSetId)).thenReturn(List.of(proposalId));
        when(executionRepository.findProposalById(proposalId)).thenReturn(Optional.of(proposal));
        when(wikimediaAttributionService.fetchAttribution(sourceUrl)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(changeSetId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("attribution is no longer available");

        verify(hintRepository, never()).save(any());
    }

    private AiChangeSetService newService() {
        return new AiChangeSetService(
                executionRepository,
                hintRepository,
                deduplicationService,
                wikimediaAttributionService,
                Optional.empty(),
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private AiChangeSet draftChangeSet(long changeSetId, long runId) {
        return new AiChangeSet(
                changeSetId,
                runId,
                AiChangeSetType.REVIEW,
                AiChangeSetStatus.DRAFT,
                "Review changes",
                "AI review",
                1,
                0,
                0,
                Map.of(),
                NOW.minusSeconds(60),
                null,
                null
        );
    }

    private AiChangeItem escalationItem(long itemId, long changeSetId, long runId, long hintId,
                                        AiChangeItemStatus status) {
        return new AiChangeItem(
                itemId,
                changeSetId,
                runId,
                null,
                AiChangeItemType.HINT_ESCALATE,
                status,
                hintId,
                401L,
                "Needs a human",
                null,
                Map.of("hintText", "An existing clue"),
                null,
                NOW.minusSeconds(30),
                null
        );
    }
}
