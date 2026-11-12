package io.clueterra.questions.clients.wikidata.cinema.hintgeneration;

import io.clueterra.questions.clients.wikidata.cinema.WikidataCinemaFact;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class WikidataCinemaAmbiguityCalculator {

    public int compute(WikidataCinemaFact fact,
                       Map<String, Set<Long>> countriesByItem,
                       Long countryId) {
        return (int) countriesByItem.getOrDefault(fact.ambiguityKey(), Set.of()).stream()
                .filter(otherCountryId -> !otherCountryId.equals(countryId))
                .count();
    }
}
