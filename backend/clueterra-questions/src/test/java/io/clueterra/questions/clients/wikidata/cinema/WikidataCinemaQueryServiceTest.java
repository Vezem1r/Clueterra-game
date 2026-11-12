package io.clueterra.questions.clients.wikidata.cinema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.wikidata.WikidataSparqlService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikidataCinemaQueryServiceTest {

    private static final String QUERY_URL = "https://query.wikidata.org/sparql";
    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    @Test
    void mergesDynamicActorAndDirectorRowsForTheSamePerson() throws Exception {
        WikidataSparqlService sparqlService = mock(WikidataSparqlService.class);
        when(sparqlService.executeQuery(eq(QUERY_URL), anyString(), eq(TIMEOUT)))
                .thenReturn(bindings("""
                        {"country":{"value":"http://www.wikidata.org/entity/Q40"},
                         "person":{"value":"http://www.wikidata.org/entity/Q2685"},
                         "occupation":{"value":"http://www.wikidata.org/entity/Q10800557"},
                         "sitelinks":{"value":"153"}},
                        {"country":{"value":"http://www.wikidata.org/entity/Q40"},
                         "person":{"value":"http://www.wikidata.org/entity/Q2685"},
                         "occupation":{"value":"http://www.wikidata.org/entity/Q2526255"},
                         "sitelinks":{"value":"153"}}
                        """));
        WikidataCinemaQueryService service = new WikidataCinemaQueryService(sparqlService);

        List<WikidataCinemaPersonCandidate> candidates = service.fetchAllPeople(QUERY_URL, TIMEOUT);

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.countryItemId()).isEqualTo("Q40");
            assertThat(candidate.personItemId()).isEqualTo("Q2685");
            assertThat(candidate.factTypes()).containsExactlyInAnyOrder(
                    WikidataCinemaFactType.ACTOR_WORKS,
                    WikidataCinemaFactType.DIRECTOR_WORKS);
            assertThat(candidate.sitelinks()).isEqualTo(153);
        });
        verify(sparqlService).executeQuery(
                eq(QUERY_URL),
                argThat(query -> query.contains("?birthPlace wdt:P17 ?country")),
                eq(TIMEOUT));
    }

    @Test
    void parsesFilmsForEachDynamicRole() throws Exception {
        WikidataSparqlService sparqlService = mock(WikidataSparqlService.class);
        when(sparqlService.executeQuery(eq(QUERY_URL), anyString(), eq(TIMEOUT)))
                .thenReturn(bindings("""
                        {"person":{"value":"http://www.wikidata.org/entity/Q2685"},
                         "factType":{"value":"ACTOR_WORKS"},
                         "work":{"value":"http://www.wikidata.org/entity/Q162255"},
                         "sitelinks":{"value":"127"}},
                        {"person":{"value":"http://www.wikidata.org/entity/Q44131"},
                         "factType":{"value":"DIRECTOR_WORKS"},
                         "work":{"value":"http://www.wikidata.org/entity/Q698111"},
                         "sitelinks":{"value":"45"}}
                        """));
        WikidataCinemaQueryService service = new WikidataCinemaQueryService(sparqlService);
        List<WikidataCinemaPersonCandidate> candidates = List.of(
                person("Q40", "Q2685"),
                new WikidataCinemaPersonCandidate(
                        "Q183", "Q44131", Set.of(WikidataCinemaFactType.DIRECTOR_WORKS), 80));

        assertThat(service.fetchWorks(QUERY_URL, candidates, TIMEOUT))
                .extracting(WikidataCinemaWorkCandidate::workItemId)
                .containsExactlyInAnyOrder("Q162255", "Q698111");
    }

    private WikidataCinemaPersonCandidate person(String countryItemId, String personItemId) {
        return new WikidataCinemaPersonCandidate(
                countryItemId, personItemId, Set.of(WikidataCinemaFactType.ACTOR_WORKS), 90);
    }

    private JsonNode bindings(String rows) throws Exception {
        return new ObjectMapper().readTree("[" + rows + "]");
    }
}
