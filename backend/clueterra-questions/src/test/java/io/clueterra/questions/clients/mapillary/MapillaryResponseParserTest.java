package io.clueterra.questions.clients.mapillary;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapillaryResponseParserTest {

    private final MapillaryResponseParser parser = new MapillaryResponseParser(new ObjectMapper());

    @Test
    void skipsPoorCandidatesAndKeepsFullResolutionUrl() {
        var results = parser.parse("""
                {"data":[
                  {"id":"1","is_pano":true,"width":1440,"height":720,"quality_score":0.85,
                   "thumb_original_url":"https://example.test/poor.jpg",
                   "geometry":{"coordinates":[14.4,50.1]},"captured_at":1700000000000},
                  {"id":"2","is_pano":true,"width":7680,"height":3840,"quality_score":0.83,
                   "thumb_original_url":"https://example.test/full.jpg",
                   "geometry":{"coordinates":[14.5,50.2]},"captured_at":1700000000000}
                ]}
                """);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo(2L);
            assertThat(result.imageUrl()).isEqualTo("https://example.test/full.jpg");
            assertThat(result.qualityScore()).isEqualTo(0.83);
        });
    }
}
