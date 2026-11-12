package io.clueterra.budget.source.wikidata;

import java.util.List;
import java.util.Objects;

import io.clueterra.budget.project.WikidataCandidate;

public record WikidataEnrichmentBatch(List<WikidataEnrichmentResult> results) {

    public WikidataEnrichmentBatch {
        results = List.copyOf(results);
    }

    public List<WikidataCandidate> candidates() {
        return results.stream()
                .map(WikidataEnrichmentResult::candidate)
                .filter(Objects::nonNull)
                .toList();
    }
}
