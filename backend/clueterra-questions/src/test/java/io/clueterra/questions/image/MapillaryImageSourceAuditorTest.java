package io.clueterra.questions.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapillaryImageSourceAuditorTest {

    private final HttpClientHelper httpClient = mock(HttpClientHelper.class);
    private final MapillaryImageSourceAuditor auditor = new MapillaryImageSourceAuditor(
            httpClient, new ObjectMapper(), properties());

    @Test
    void validatesImageAndBackfillsCreatorAttribution() {
        when(httpClient.get(
                eq(DataSource.MAPILLARY),
                contains("/123456?"),
                eq(Duration.ofSeconds(30))))
                .thenReturn("""
                        {
                          "id": "123456",
                          "thumb_2048_url": "https://scontent.example.test/image.jpg",
                          "thumb_original_url": "https://scontent.example.test/original.jpg",
                          "width": 7680,
                          "height": 3840,
                          "creator": {"username": "alice"}
                        }
                        """);

        ImageSourceValidation result = auditor.validate(candidate());

        assertThat(result.status()).isEqualTo(ImageSourceValidationStatus.AVAILABLE);
        assertThat(result.metadata().authorName()).isEqualTo("alice");
        assertThat(result.metadata().attributionText()).isEqualTo("Mapillary image by @alice.");
        assertThat(result.metadata().sourcePageUrl()).contains("pKey=123456");
        assertThat(result.metadata().downloadUrl()).isEqualTo("https://scontent.example.test/original.jpg");
        assertThat(result.metadata().width()).isEqualTo(7680);
        assertThat(result.metadata().height()).isEqualTo(3840);
    }

    @Test
    void genericApiErrorsAreTransientAndDoNotQuarantineImages() {
        when(httpClient.get(
                eq(DataSource.MAPILLARY),
                contains("/123456?"),
                eq(Duration.ofSeconds(30))))
                .thenReturn("""
                        {"error":{"code":100,"message":"Invalid parameter"}}
                        """);

        ImageSourceValidation result = auditor.validate(candidate());

        assertThat(result.status()).isEqualTo(ImageSourceValidationStatus.FETCH_FAILED);
    }

    @Test
    void usesThumbnailOnlyWhenOriginalIsUnavailable() {
        when(httpClient.get(
                eq(DataSource.MAPILLARY),
                contains("/123456?"),
                eq(Duration.ofSeconds(30))))
                .thenReturn("""
                        {"id":"123456","thumb_2048_url":"https://scontent.example.test/preview.jpg"}
                        """);

        ImageSourceValidation result = auditor.validate(candidate());

        assertThat(result.status()).isEqualTo(ImageSourceValidationStatus.AVAILABLE);
        assertThat(result.metadata().downloadUrl())
                .isEqualTo("https://scontent.example.test/preview.jpg");
    }

    private SourcesProperties properties() {
        SourceProperties mapillary = new SourceProperties();
        mapillary.setEnabled(true);
        mapillary.setBaseUrl("https://graph.mapillary.com");
        mapillary.setApiKey("test-token");
        mapillary.setTimeoutSeconds(30);
        SourcesProperties properties = new SourcesProperties();
        properties.setSources(Map.of(DataSource.MAPILLARY.getConfigKey(), mapillary));
        return properties;
    }

    private ImageAuditCandidate candidate() {
        return new ImageAuditCandidate(
                ImageAssetOwnerType.HINT,
                42L,
                "Mapillary panorama",
                "CZ",
                "MAPILLARY",
                "MAPILLARY_PANORAMA",
                "mapillary/cz/123456.jpg",
                "https://scontent.example.test/image.jpg",
                "https://www.mapillary.com/app/?pKey=123456&focus=photo",
                null,
                "Street-level imagery via Mapillary.",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/",
                "https://scontent.example.test/image.jpg",
                9L,
                "MAPILLARY",
                "https://www.mapillary.com/app/?pKey=123456&focus=photo",
                "hint-images",
                "mapillary/cz/123456.jpg",
                null,
                "ACTIVE",
                0,
                1);
    }
}
