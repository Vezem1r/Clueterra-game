package io.clueterra.questions.clients.wikidata.cinema;

import java.util.Set;

public record WikidataCinemaSelection(
        String countryCode,
        String personItemId,
        WikidataCinemaFactType factType,
        String workOneItemId,
        String workTwoItemId,
        int personSitelinks,
        int workOneSitelinks,
        int workTwoSitelinks
) {
    public Set<String> referencedItemIds() {
        return Set.of(personItemId, workOneItemId, workTwoItemId);
    }

    public int workRecognition() {
        return workOneSitelinks + workTwoSitelinks;
    }
}
