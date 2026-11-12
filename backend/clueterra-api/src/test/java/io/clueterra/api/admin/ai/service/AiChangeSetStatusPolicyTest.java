package io.clueterra.api.admin.ai.service;

import io.clueterra.api.admin.ai.dto.AiChangeItemDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiChangeSetStatusPolicyTest {

    @Test
    void emptySetIsDraft() {
        assertThat(AiChangeSetStatusPolicy.resolveSetStatus(List.of()))
                .isEqualTo(AiChangeSetStatusPolicy.SET_DRAFT);
    }

    @Test
    void untouchedSetIsDraft() {
        assertThat(resolve(AiChangeSetStatusPolicy.ITEM_PENDING, AiChangeSetStatusPolicy.ITEM_PENDING))
                .isEqualTo(AiChangeSetStatusPolicy.SET_DRAFT);
    }

    @Test
    void fullyApprovedSetStaysDraftBecauseApprovingOnlyMarksIntent() {
        assertThat(resolve(AiChangeSetStatusPolicy.ITEM_APPROVED, AiChangeSetStatusPolicy.ITEM_APPROVED))
                .isEqualTo(AiChangeSetStatusPolicy.SET_DRAFT);
    }

    @Test
    void approvedAndRejectedMixStaysDraftWhileAnythingAwaitsApply() {
        assertThat(resolve(AiChangeSetStatusPolicy.ITEM_APPROVED, AiChangeSetStatusPolicy.ITEM_REJECTED))
                .isEqualTo(AiChangeSetStatusPolicy.SET_DRAFT);
    }

    @Test
    void fullyRejectedSetIsRejected() {
        assertThat(resolve(AiChangeSetStatusPolicy.ITEM_REJECTED, AiChangeSetStatusPolicy.ITEM_REJECTED))
                .isEqualTo(AiChangeSetStatusPolicy.SET_REJECTED);
    }

    @Test
    void fullyAppliedSetIsApplied() {
        assertThat(resolve(AiChangeSetStatusPolicy.ITEM_APPLIED, AiChangeSetStatusPolicy.ITEM_APPLIED))
                .isEqualTo(AiChangeSetStatusPolicy.SET_APPLIED);
    }

    @Test
    void fullyCancelledSetIsCancelledAndCannotBeMarkedAgain() {
        assertThat(resolve(AiChangeSetStatusPolicy.ITEM_CANCELLED))
                .isEqualTo(AiChangeSetStatusPolicy.SET_CANCELLED);
        assertThat(AiChangeSetStatusPolicy.isTerminal(AiChangeSetStatusPolicy.ITEM_CANCELLED)).isTrue();
    }

    @Test
    void appliedAndRejectedMixIsPartiallyApplied() {
        assertThat(resolve(AiChangeSetStatusPolicy.ITEM_APPLIED, AiChangeSetStatusPolicy.ITEM_REJECTED))
                .isEqualTo(AiChangeSetStatusPolicy.SET_PARTIALLY_APPLIED);
    }

    @Test
    void appliedSetWithALeftoverOpenItemIsStillDraft() {
        assertThat(resolve(AiChangeSetStatusPolicy.ITEM_APPLIED, AiChangeSetStatusPolicy.ITEM_PENDING))
                .isEqualTo(AiChangeSetStatusPolicy.SET_DRAFT);
    }

    @Test
    void onlyAppliedItemsAreTerminal() {
        assertThat(AiChangeSetStatusPolicy.isTerminal(AiChangeSetStatusPolicy.ITEM_APPLIED)).isTrue();
        assertThat(AiChangeSetStatusPolicy.isTerminal(AiChangeSetStatusPolicy.ITEM_PENDING)).isFalse();
        assertThat(AiChangeSetStatusPolicy.isTerminal(AiChangeSetStatusPolicy.ITEM_APPROVED)).isFalse();
        assertThat(AiChangeSetStatusPolicy.isTerminal(AiChangeSetStatusPolicy.ITEM_REJECTED)).isFalse();
    }

    @Test
    void pendingAndApprovedItemsAwaitApply() {
        assertThat(AiChangeSetStatusPolicy.isAwaitingApply(AiChangeSetStatusPolicy.ITEM_PENDING)).isTrue();
        assertThat(AiChangeSetStatusPolicy.isAwaitingApply(AiChangeSetStatusPolicy.ITEM_APPROVED)).isTrue();
        assertThat(AiChangeSetStatusPolicy.isAwaitingApply(AiChangeSetStatusPolicy.ITEM_REJECTED)).isFalse();
        assertThat(AiChangeSetStatusPolicy.isAwaitingApply(AiChangeSetStatusPolicy.ITEM_APPLIED)).isFalse();
    }

    private String resolve(String... itemStatuses) {
        List<AiChangeItemDto> items = Arrays.stream(itemStatuses)
                .map(AiChangeSetStatusPolicyTest::item)
                .toList();
        return AiChangeSetStatusPolicy.resolveSetStatus(items);
    }

    private static AiChangeItemDto item(String status) {
        return new AiChangeItemDto(1L, 2L, 3L, null, "HINT_UPDATE", status,
                null, null, null, null, Map.of(), Map.of(), null, null);
    }
}
