package io.clueterra.budget.source.wikidata;

import io.clueterra.budget.project.WikidataCandidate;

public record WikidataCandidateEvaluation(
        WikidataCandidate candidate, WikidataRejectionReason rejectionReason) {

    public static WikidataCandidateEvaluation ready(WikidataCandidate candidate) {
        return new WikidataCandidateEvaluation(candidate, null);
    }

    public static WikidataCandidateEvaluation rejected(WikidataRejectionReason reason) {
        return new WikidataCandidateEvaluation(null, reason);
    }
}
