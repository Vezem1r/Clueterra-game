package io.clueterra.api.admin.ai.service;

import io.clueterra.api.admin.ai.dto.AiChangeItemDto;

import java.util.List;

public final class AiChangeSetStatusPolicy {

    public static final String ITEM_PENDING = "PENDING";
    public static final String ITEM_APPROVED = "APPROVED";
    public static final String ITEM_REJECTED = "REJECTED";
    public static final String ITEM_APPLIED = "APPLIED";
    public static final String ITEM_CANCELLED = "CANCELLED";

    public static final String SET_DRAFT = "DRAFT";
    public static final String SET_APPLIED = "APPLIED";
    public static final String SET_REJECTED = "REJECTED";
    public static final String SET_PARTIALLY_APPLIED = "PARTIALLY_APPLIED";
    public static final String SET_CANCELLED = "CANCELLED";

    private AiChangeSetStatusPolicy() {
    }

    public static boolean isTerminal(String itemStatus) {
        return ITEM_APPLIED.equals(itemStatus) || ITEM_CANCELLED.equals(itemStatus);
    }

    public static boolean isAwaitingApply(String itemStatus) {
        return ITEM_PENDING.equals(itemStatus) || ITEM_APPROVED.equals(itemStatus);
    }

    public static int count(List<AiChangeItemDto> items, String itemStatus) {
        return (int) items.stream().filter(item -> itemStatus.equals(item.status())).count();
    }

    public static int countAwaitingApply(List<AiChangeItemDto> items) {
        return (int) items.stream().filter(item -> isAwaitingApply(item.status())).count();
    }

    public static String resolveSetStatus(List<AiChangeItemDto> items) {
        if (items.isEmpty() || countAwaitingApply(items) > 0) {
            return SET_DRAFT;
        }
        if (count(items, ITEM_APPLIED) == items.size()) {
            return SET_APPLIED;
        }
        if (count(items, ITEM_REJECTED) == items.size()) {
            return SET_REJECTED;
        }
        if (count(items, ITEM_CANCELLED) == items.size()) {
            return SET_CANCELLED;
        }
        return SET_PARTIALLY_APPLIED;
    }
}
