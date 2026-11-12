package io.clueterra.api.admin.budgetcontent.dto;

import java.util.List;

public enum BudgetContentTab {
    REVIEW(List.of("NEEDS_REVIEW", "NEEDS_IMAGE", "DISCOVERED")),
    APPROVED(List.of("EDITORIAL_APPROVED", "APPROVED")),
    REJECTED(List.of("REJECTED"));

    private final List<String> statuses;

    BudgetContentTab(List<String> statuses) {
        this.statuses = statuses;
    }

    public List<String> statuses() {
        return statuses;
    }
}
