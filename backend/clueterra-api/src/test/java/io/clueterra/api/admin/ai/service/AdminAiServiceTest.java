package io.clueterra.api.admin.ai.service;

import io.clueterra.api.admin.ai.dto.AiChangeItemDto;
import io.clueterra.api.admin.ai.dto.AiChangeSetActionResponse;
import io.clueterra.api.admin.ai.dto.AiChangeSetDto;
import io.clueterra.api.admin.ai.repository.AdminAiJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAiServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final long CHANGE_SET_ID = 10L;
    private static final long RUN_ID = 20L;
    private static final long ITEM_ID = 30L;
    private static final long PROPOSAL_ID = 40L;
    private static final long HINT_ID = 50L;

    @Mock
    private AdminAiJdbcRepository repository;

    private AdminAiService service;

    @BeforeEach
    void setUp() {
        service = new AdminAiService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void approveOnlyMarksTheItemAndLeavesTheSetWorkable() {
        when(repository.findChangeSetByIdForUpdate(CHANGE_SET_ID)).thenReturn(Optional.of(changeSet()));
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item(AiChangeSetStatusPolicy.ITEM_PENDING)));
        when(repository.findItemsByChangeSetId(CHANGE_SET_ID))
                .thenReturn(List.of(item(AiChangeSetStatusPolicy.ITEM_APPROVED)));

        AiChangeSetActionResponse response = service.approveItem(CHANGE_SET_ID, ITEM_ID);

        verify(repository).updateItemStatus(ITEM_ID, AiChangeSetStatusPolicy.ITEM_APPROVED, NOW);
        verify(repository, never()).markHintForReviewRetry(HINT_ID, RUN_ID, NOW);
        assertThat(response.status()).isEqualTo(AiChangeSetStatusPolicy.SET_DRAFT);
        assertThat(response.appliedItems()).isZero();
        assertThat(response.rejectedItems()).isZero();
    }

    @Test
    void approveAfterRejectRestoresTheProposalSoApplyStaysConsistent() {
        when(repository.findChangeSetByIdForUpdate(CHANGE_SET_ID)).thenReturn(Optional.of(changeSet()));
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item(AiChangeSetStatusPolicy.ITEM_REJECTED)));
        when(repository.findItemsByChangeSetId(CHANGE_SET_ID))
                .thenReturn(List.of(item(AiChangeSetStatusPolicy.ITEM_APPROVED)));

        AiChangeSetActionResponse response = service.approveItem(CHANGE_SET_ID, ITEM_ID);

        verify(repository).updateItemStatus(ITEM_ID, AiChangeSetStatusPolicy.ITEM_APPROVED, NOW);
        verify(repository).updateProposalStatus(PROPOSAL_ID, "PENDING_APPROVAL");
        assertThat(response.status()).isEqualTo(AiChangeSetStatusPolicy.SET_DRAFT);
    }

    @Test
    void approveRejectedReviewEditPausesRetryUntilApply() {
        when(repository.findChangeSetByIdForUpdate(CHANGE_SET_ID)).thenReturn(Optional.of(changeSet()));
        when(repository.findItemById(ITEM_ID))
                .thenReturn(Optional.of(reviewItem(AiChangeSetStatusPolicy.ITEM_REJECTED)));
        when(repository.findItemsByChangeSetId(CHANGE_SET_ID))
                .thenReturn(List.of(reviewItem(AiChangeSetStatusPolicy.ITEM_APPROVED)));

        service.approveItem(CHANGE_SET_ID, ITEM_ID);

        verify(repository).markHintReviewActionPending(HINT_ID);
    }

    @Test
    void rejectGeneratedProposalDoesNotChangeExistingHintReviewState() {
        when(repository.findChangeSetByIdForUpdate(CHANGE_SET_ID)).thenReturn(Optional.of(changeSet()));
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item(AiChangeSetStatusPolicy.ITEM_PENDING)));
        when(repository.findItemsByChangeSetId(CHANGE_SET_ID))
                .thenReturn(List.of(item(AiChangeSetStatusPolicy.ITEM_REJECTED)));

        AiChangeSetActionResponse response = service.rejectItem(CHANGE_SET_ID, ITEM_ID);

        verify(repository).updateItemStatus(ITEM_ID, AiChangeSetStatusPolicy.ITEM_REJECTED, NOW);
        verify(repository).updateProposalStatus(PROPOSAL_ID, AiChangeSetStatusPolicy.ITEM_REJECTED);
        verify(repository, never()).markHintForReviewRetry(HINT_ID, RUN_ID, NOW);
        assertThat(response.status()).isEqualTo(AiChangeSetStatusPolicy.SET_REJECTED);
        assertThat(response.rejectedItems()).isEqualTo(1);
    }

    @Test
    void rejectReviewEditQueuesHintForAnotherReview() {
        AiChangeItemDto reviewItem = reviewItem(AiChangeSetStatusPolicy.ITEM_PENDING);
        when(repository.findChangeSetByIdForUpdate(CHANGE_SET_ID)).thenReturn(Optional.of(changeSet()));
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(reviewItem));
        when(repository.findItemsByChangeSetId(CHANGE_SET_ID))
                .thenReturn(List.of(reviewItem(AiChangeSetStatusPolicy.ITEM_REJECTED)));

        AiChangeSetActionResponse response = service.rejectItem(CHANGE_SET_ID, ITEM_ID);

        verify(repository).updateItemStatus(ITEM_ID, AiChangeSetStatusPolicy.ITEM_REJECTED, NOW);
        verify(repository).markHintForReviewRetry(HINT_ID, RUN_ID, NOW);
        assertThat(response.status()).isEqualTo(AiChangeSetStatusPolicy.SET_REJECTED);
    }

    @Test
    void anAppliedItemIsAnIdempotentNoOp() {
        when(repository.findChangeSetByIdForUpdate(CHANGE_SET_ID)).thenReturn(Optional.of(changeSet()));
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item(AiChangeSetStatusPolicy.ITEM_APPLIED)));
        when(repository.findItemsByChangeSetId(CHANGE_SET_ID))
                .thenReturn(List.of(item(AiChangeSetStatusPolicy.ITEM_APPLIED)));

        AiChangeSetActionResponse response = service.approveItem(CHANGE_SET_ID, ITEM_ID);

        verify(repository, never()).updateItemStatus(ITEM_ID, AiChangeSetStatusPolicy.ITEM_APPROVED, NOW);
        assertThat(response.message()).isEqualTo("Item is already finalized");
    }

    @Test
    void rejectingAnAppliedItemIsAlsoAnIdempotentNoOp() {
        when(repository.findChangeSetByIdForUpdate(CHANGE_SET_ID)).thenReturn(Optional.of(changeSet()));
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item(AiChangeSetStatusPolicy.ITEM_APPLIED)));
        when(repository.findItemsByChangeSetId(CHANGE_SET_ID))
                .thenReturn(List.of(item(AiChangeSetStatusPolicy.ITEM_APPLIED)));

        AiChangeSetActionResponse response = service.rejectItem(CHANGE_SET_ID, ITEM_ID);

        verify(repository, never()).updateItemStatus(ITEM_ID, AiChangeSetStatusPolicy.ITEM_REJECTED, NOW);
        assertThat(response.message()).isEqualTo("Item is already finalized");
    }

    @Test
    void autoRejectedAnswerLeakCannotBeApprovedLater() {
        when(repository.findChangeSetByIdForUpdate(CHANGE_SET_ID)).thenReturn(Optional.of(changeSet()));
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(new AiChangeItemDto(
                ITEM_ID, CHANGE_SET_ID, RUN_ID, null, "HINT_UPDATE", "REJECTED", HINT_ID, 60L,
                "Answer leak", "Diff", Map.of(), Map.of("autoRejected", true), NOW.minusSeconds(30), NOW)));

        assertThatThrownBy(() -> service.approveItem(CHANGE_SET_ID, ITEM_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("answer-leak safety check");

        verify(repository, never()).updateItemStatus(ITEM_ID, AiChangeSetStatusPolicy.ITEM_APPROVED, NOW);
    }

    @Test
    void rejectAllKeepsManuallyApprovedItemsForApply() {
        when(repository.findChangeSetByIdForUpdate(CHANGE_SET_ID)).thenReturn(Optional.of(changeSet()));
        AiChangeSetActionResponse expected = new AiChangeSetActionResponse(
                String.valueOf(CHANGE_SET_ID), AiChangeSetStatusPolicy.SET_DRAFT, 2, 0, 1, "1 item(s) rejected");
        when(repository.decideChangeItems(
                CHANGE_SET_ID,
                List.of("HINT_UPDATE", "HINT_DISABLE", "HINT_CREATE", "IMAGE_REPLACE", "HINT_ESCALATE"),
                AiChangeSetStatusPolicy.ITEM_REJECTED)).thenReturn(expected);

        AiChangeSetActionResponse response = service.rejectAll(CHANGE_SET_ID);

        verify(repository).decideChangeItems(
                CHANGE_SET_ID,
                List.of("HINT_UPDATE", "HINT_DISABLE", "HINT_CREATE", "IMAGE_REPLACE", "HINT_ESCALATE"),
                AiChangeSetStatusPolicy.ITEM_REJECTED);
        assertThat(response.status()).isEqualTo(AiChangeSetStatusPolicy.SET_DRAFT);
        assertThat(response.rejectedItems()).isEqualTo(1);
    }

    @Test
    void rejectAllSkipsAppliedItemsAndReportsPartiallyApplied() {
        when(repository.findChangeSetByIdForUpdate(CHANGE_SET_ID)).thenReturn(Optional.of(changeSet()));
        AiChangeSetActionResponse expected = new AiChangeSetActionResponse(
                String.valueOf(CHANGE_SET_ID), AiChangeSetStatusPolicy.SET_PARTIALLY_APPLIED,
                2, 1, 1, "1 item(s) rejected");
        when(repository.decideChangeItems(
                CHANGE_SET_ID,
                List.of("HINT_UPDATE", "HINT_DISABLE", "HINT_CREATE", "IMAGE_REPLACE", "HINT_ESCALATE"),
                AiChangeSetStatusPolicy.ITEM_REJECTED)).thenReturn(expected);

        AiChangeSetActionResponse response = service.rejectAll(CHANGE_SET_ID);

        verify(repository).decideChangeItems(
                CHANGE_SET_ID,
                List.of("HINT_UPDATE", "HINT_DISABLE", "HINT_CREATE", "IMAGE_REPLACE", "HINT_ESCALATE"),
                AiChangeSetStatusPolicy.ITEM_REJECTED);
        assertThat(response.status()).isEqualTo(AiChangeSetStatusPolicy.SET_PARTIALLY_APPLIED);
        assertThat(response.appliedItems()).isEqualTo(1);
        assertThat(response.rejectedItems()).isEqualTo(1);
    }

    private AiChangeSetDto changeSet() {
        return new AiChangeSetDto(CHANGE_SET_ID, RUN_ID, "REVIEW", AiChangeSetStatusPolicy.SET_DRAFT,
                "Review changes", "AI review", 1, 0, 0, Map.of(), NOW.minusSeconds(60), null, null);
    }

    private AiChangeItemDto item(String status) {
        return item(ITEM_ID, status);
    }

    private AiChangeItemDto item(Long id, String status) {
        return new AiChangeItemDto(id, CHANGE_SET_ID, RUN_ID, PROPOSAL_ID, "HINT_UPDATE", status,
                HINT_ID, 60L, "Reason", "Diff", Map.of(), Map.of(), NOW.minusSeconds(30), null);
    }

    private AiChangeItemDto reviewItem(String status) {
        return new AiChangeItemDto(ITEM_ID, CHANGE_SET_ID, RUN_ID, null, "HINT_UPDATE", status,
                HINT_ID, 60L, "Reason", "Diff", Map.of(), Map.of(), NOW.minusSeconds(30), null);
    }
}
