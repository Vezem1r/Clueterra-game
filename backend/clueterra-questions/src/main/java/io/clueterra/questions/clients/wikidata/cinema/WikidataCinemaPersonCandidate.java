package io.clueterra.questions.clients.wikidata.cinema;

import java.util.Set;

public record WikidataCinemaPersonCandidate(
        String countryItemId,
        String personItemId,
        Set<WikidataCinemaFactType> factTypes,
        int sitelinks
) {
    public WikidataCinemaPersonCandidate {
        factTypes = Set.copyOf(factTypes);
    }
}
