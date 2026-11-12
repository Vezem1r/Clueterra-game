package io.clueterra.questions.clients.wikidata.cinema;

public record WikidataCinemaWorkCandidate(
        String personItemId,
        WikidataCinemaFactType factType,
        String workItemId,
        int sitelinks
) {
}
