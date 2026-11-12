package io.clueterra.budget.source.wikidata;

public record WikipediaPageSignals(
        int monthlyPageviews, String description, String descriptionSourceUrl) {

    public static WikipediaPageSignals empty() {
        return new WikipediaPageSignals(0, "", "");
    }
}
