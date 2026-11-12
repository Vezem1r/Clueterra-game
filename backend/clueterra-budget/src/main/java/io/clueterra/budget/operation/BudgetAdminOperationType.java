package io.clueterra.budget.operation;

public enum BudgetAdminOperationType {
    FULL_SYNC("Full Content Sync"),
    WIKIDATA_DISCOVERY("Wikidata Discovery"),
    IMAGE_ENRICHMENT("Image Enrichment"),
    QUESTION_GENERATION("Question Generation"),
    DAILY_CHALLENGE_GENERATION("Daily Challenge Generation");

    private final String displayName;

    BudgetAdminOperationType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
