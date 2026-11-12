package io.clueterra.budget.source.wikidata;

import java.util.List;
import java.util.UUID;

import io.clueterra.budget.project.WikidataCandidate;

public record WikidataCategoryDiscoveryResult(
        UUID snapshotId,
        int discoveredCount,
        List<WikidataCandidate> candidates,
        int remainingCount,
        List<String> deferredFailures) {

    public WikidataCategoryDiscoveryResult {
        candidates = List.copyOf(candidates);
        deferredFailures = List.copyOf(deferredFailures);
    }

    public boolean complete() {
        return remainingCount == 0;
    }
}
