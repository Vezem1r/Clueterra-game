package io.clueterra.questions.clients.wikidata.cinema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WikidataCinemaStatisticCodeTest {

    @Test
    void roundTripsPersonAndWorkCodesWithinStatisticColumnLimit() {
        String personCode = WikidataCinemaStatisticCode.person(
                WikidataCinemaFactType.ACTOR_WORKS, "Q1234567890");
        String workCode = WikidataCinemaStatisticCode.work(
                WikidataCinemaFactType.ACTOR_WORKS, "Q1234567890", "Q9876543210");

        assertThat(personCode).hasSizeLessThanOrEqualTo(50);
        assertThat(workCode).hasSizeLessThanOrEqualTo(50);
        assertThat(WikidataCinemaStatisticCode.parsePerson(personCode))
                .contains(new WikidataCinemaPersonCode(
                        WikidataCinemaFactType.ACTOR_WORKS, "Q1234567890"));
        assertThat(WikidataCinemaStatisticCode.parseWork(workCode))
                .contains(new WikidataCinemaWorkCode(
                        WikidataCinemaFactType.ACTOR_WORKS, "Q1234567890", "Q9876543210"));
    }
}
