package io.clueterra.questions.clients.mapillary;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapillaryPanoramaQualityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsHighResolutionCompletePanorama() throws Exception {
        var image = objectMapper.readTree("""
                {
                  "is_pano": true,
                  "width": 7680,
                  "height": 3840,
                  "quality_score": 0.84,
                  "thumb_original_url": "https://example.test/original.jpg"
                }
                """);

        assertThat(MapillaryPanoramaQuality.usableOriginalUrl(image))
                .contains("https://example.test/original.jpg");
    }

    @Test
    void rejectsLowResolutionLowScoreAndIncompletePanoramas() throws Exception {
        var lowResolution = objectMapper.readTree("""
                {"is_pano":true,"width":1440,"height":720,"quality_score":0.84,
                 "thumb_original_url":"https://example.test/low-res.jpg"}
                """);
        var lowScore = objectMapper.readTree("""
                {"is_pano":true,"width":7680,"height":3840,"quality_score":0.40,
                 "thumb_original_url":"https://example.test/low-score.jpg"}
                """);
        var incomplete = objectMapper.readTree("""
                {"is_pano":true,"width":7680,"height":1800,"quality_score":0.84,
                 "thumb_original_url":"https://example.test/incomplete.jpg"}
                """);

        assertThat(MapillaryPanoramaQuality.usableOriginalUrl(lowResolution)).isEmpty();
        assertThat(MapillaryPanoramaQuality.usableOriginalUrl(lowScore)).isEmpty();
        assertThat(MapillaryPanoramaQuality.usableOriginalUrl(incomplete)).isEmpty();
    }
}
