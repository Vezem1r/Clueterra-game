package io.clueterra.questions.clients.citybikes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkDetail;
import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkIndexEntry;
import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CityBikesParserTest {

    private final CityBikesParser parser = new CityBikesParser(new ObjectMapper());

    @Test
    void parseNetworkIndexKeepsOnlyEntriesWithRequiredFieldsAndNormalizesCompanies() {
        String body = """
                {
                  "networks": [
                    {
                      "id": "velib",
                      "name": "Velib",
                      "href": "/v2/networks/velib",
                      "company": ["Smovengo", "Paris City"],
                      "location": {"city": "Paris", "country": "FR"}
                    },
                    {
                      "id": "missing-country",
                      "name": "Broken",
                      "location": {"city": "Nowhere"}
                    },
                    {
                      "id": "",
                      "name": "Missing Id",
                      "location": {"city": "Rome", "country": "IT"}
                    }
                  ]
                }
                """;

        List<CityBikesNetworkIndexEntry> entries = parser.parseNetworkIndex(body);

        assertThat(entries)
                .extracting(CityBikesNetworkIndexEntry::id,
                        CityBikesNetworkIndexEntry::countryCode,
                        CityBikesNetworkIndexEntry::company)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("velib", "FR", "Smovengo / Paris City"));
    }

    @Test
    void parseNetworkDetailAggregatesStationTotalsAndFallsBackToSummaryCompany() {
        String body = """
                {
                  "network": {
                    "name": "Bicing",
                    "location": {"city": "Barcelona"},
                    "stations": [
                      {"free_bikes": 5, "empty_slots": 7, "extra": {"ebikes": 2}},
                      {"free_bikes": 3, "empty_slots": 4, "extra": {"ebikes": 1}}
                    ]
                  }
                }
                """;

        CityBikesNetworkSummary summary = new CityBikesNetworkSummary(
                "bicing",
                "Bicing",
                "Barcelona",
                "ES",
                "/v2/networks/bicing",
                "Ajuntament"
        );

        CityBikesNetworkDetail detail = parser.parseNetworkDetail(body, summary).orElseThrow();

        assertThat(detail.stationCount()).isEqualTo(2);
        assertThat(detail.freeBikes()).isEqualTo(8);
        assertThat(detail.emptySlots()).isEqualTo(11);
        assertThat(detail.ebikes()).isEqualTo(3);
        assertThat(detail.company()).isEqualTo("Ajuntament");
        assertThat(detail.subject()).isEqualTo("Bicing in Barcelona");
    }
}
