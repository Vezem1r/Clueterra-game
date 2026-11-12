package io.clueterra.questions.clients.wikidata.cinema;

import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.wikidata.WikidataStatementService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikidataCinemaServiceTest {

    @Test
    void buildsCinemaFactsEntirelyFromDynamicQueryResultsAndLabels() {
        WikidataCinemaQueryService queryService = mock(WikidataCinemaQueryService.class);
        WikidataStatementService statementService = mock(WikidataStatementService.class);
        WikidataCinemaPersonCandidate person = new WikidataCinemaPersonCandidate(
                "Q40", "Q2685", Set.of(WikidataCinemaFactType.ACTOR_WORKS), 153);
        Duration timeout = Duration.ofSeconds(45);
        when(queryService.fetchAllPeople("query", timeout)).thenReturn(List.of(person));
        when(queryService.fetchWorks(eq("query"), anyList(), eq(timeout))).thenReturn(List.of(
                work("Q162255", 127),
                work("Q200804", 99),
                work("Q999", 10)));
        when(statementService.resolveEnglishLabels(eq("action"), anySet(), eq(timeout))).thenReturn(Map.of(
                "Q2685", "Arnold Schwarzenegger",
                "Q162255", "The Terminator",
                "Q200804", "Predator"));
        WikidataCinemaService service = new WikidataCinemaService(
                queryService,
                statementService);

        Map<String, List<WikidataCinemaFact>> result = service.fetchAll(
                "query", "action", Map.of("AT", "Q40"), timeout);

        assertThat(result.get("AT")).singleElement().satisfies(fact -> {
            assertThat(fact.subject()).isEqualTo("Arnold Schwarzenegger");
            assertThat(fact.workOne()).isEqualTo("The Terminator");
            assertThat(fact.workTwo()).isEqualTo("Predator");
            assertThat(fact.factType()).isEqualTo(WikidataCinemaFactType.ACTOR_WORKS);
            assertThat(fact.recognitionScore()).isGreaterThan(85);
        });
        List<RawStatistic> statistics = service.toStatistics(result.get("AT"));
        assertThat(statistics)
                .extracting(RawStatistic::indicatorCode)
                .containsExactly(
                        "WIKIDATA_CINEMA_A_Q2685",
                        "WIKIDATA_CINEMA_AW_Q2685_Q162255",
                        "WIKIDATA_CINEMA_AW_Q2685_Q200804");
        assertThat(statistics)
                .extracting(RawStatistic::valueText)
                .containsExactly("Arnold Schwarzenegger", "The Terminator", "Predator");
        verify(queryService).fetchAllPeople("query", timeout);
        verify(queryService).fetchWorks(eq("query"), anyList(), eq(timeout));
    }

    private WikidataCinemaWorkCandidate work(String itemId, int sitelinks) {
        return new WikidataCinemaWorkCandidate(
                "Q2685", WikidataCinemaFactType.ACTOR_WORKS, itemId, sitelinks);
    }
}
