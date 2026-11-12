package io.clueterra.questions.clients.wikidata.cinema;

public record WikidataCinemaWorkCode(
        WikidataCinemaFactType factType,
        String personItemId,
        String workItemId
) {
}
