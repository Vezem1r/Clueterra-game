package io.clueterra.questions.clients.wikidata.cinema;

public record WikidataCinemaFact(
        String id,
        String countryCode,
        WikidataCinemaFactType factType,
        String ambiguityKey,
        String subject,
        String workOneItemId,
        String workOne,
        int workOneSitelinks,
        String workTwoItemId,
        String workTwo,
        int workTwoSitelinks,
        int personSitelinks,
        int recognitionScore,
        double confidence
) {
}
