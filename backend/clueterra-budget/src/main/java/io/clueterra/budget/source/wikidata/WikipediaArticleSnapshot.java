package io.clueterra.budget.source.wikidata;

public record WikipediaArticleSnapshot(
        String language,
        String requestedTitle,
        String resolvedTitle,
        long revisionId,
        String wikitext) {
}
