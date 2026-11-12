package io.clueterra.budget.source.wikidata;

import io.clueterra.budget.project.WikidataCandidate;

public record WikidataEnrichmentResult(
        String wikidataId, String status, String rejectionReason, WikidataCandidate candidate) {

    public static WikidataEnrichmentResult ready(WikidataCandidate candidate) {
        return new WikidataEnrichmentResult(candidate.wikidataId(), "READY", null, candidate);
    }

    public static WikidataEnrichmentResult rejected(
            String wikidataId, WikidataRejectionReason rejectionReason) {
        return new WikidataEnrichmentResult(wikidataId, "REJECTED", rejectionReason.name(), null);
    }
}
