package io.clueterra.questions.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.entity.enums.DataSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikimediaImageSourceAuditorTest {

    private final HttpClientHelper httpClient = mock(HttpClientHelper.class);
    private final WikimediaImageSourceAuditor auditor =
            new WikimediaImageSourceAuditor(httpClient, new ObjectMapper());

    @Test
    void returnsCurrentCommerciallySafeMetadata() {
        when(httpClient.get(eq(DataSource.WIKIMEDIA_COMMONS), anyString(), eq(Duration.ofSeconds(20))))
                .thenReturn("""
                        {"query":{"pages":[{"title":"File:Example.jpg","imageinfo":[{
                          "url":"https://upload.wikimedia.org/wikipedia/commons/a/a1/Example.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original",
                          "descriptionurl":"https://commons.wikimedia.org/wiki/File:Example.jpg",
                          "extmetadata":{
                            "Artist":{"value":"Alice Example"},
                            "Credit":{"value":"Own work"},
                            "LicenseShortName":{"value":"CC BY-SA 4.0"},
                            "LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0/"},
                            "UsageTerms":{"value":"Creative Commons Attribution-Share Alike 4.0"}
                          }
                        }]}]}}
                        """);

        ImageSourceValidation result = auditor.validate(candidate());

        assertThat(result.status()).isEqualTo(ImageSourceValidationStatus.AVAILABLE);
        assertThat(result.metadata().authorName()).isEqualTo("Alice Example");
        assertThat(result.metadata().licenseLabel()).isEqualTo("CC BY-SA 4.0");
        assertThat(result.metadata().originalImageUrl())
                .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/a/a1/Example.jpg");
    }

    @Test
    void distinguishesDeletedFileFromTemporaryLookupFailure() {
        when(httpClient.get(eq(DataSource.WIKIMEDIA_COMMONS), anyString(), eq(Duration.ofSeconds(20))))
                .thenReturn("{\"query\":{\"pages\":[{\"title\":\"File:Example.jpg\",\"missing\":true}]}}");

        ImageSourceValidation result = auditor.validate(candidate());

        assertThat(result.status()).isEqualTo(ImageSourceValidationStatus.SOURCE_MISSING);
        assertThat(result.reason()).contains("no longer exists");
    }

    @Test
    void reportsLicenseThatIsNoLongerCommerciallySafe() {
        when(httpClient.get(eq(DataSource.WIKIMEDIA_COMMONS), anyString(), eq(Duration.ofSeconds(20))))
                .thenReturn("""
                        {"query":{"pages":[{"title":"File:Example.jpg","imageinfo":[{
                          "url":"https://upload.wikimedia.org/wikipedia/commons/a/a1/Example.jpg",
                          "descriptionurl":"https://commons.wikimedia.org/wiki/File:Example.jpg",
                          "extmetadata":{
                            "Artist":{"value":"Alice Example"},
                            "LicenseShortName":{"value":"CC BY-NC 4.0"},
                            "LicenseUrl":{"value":"https://creativecommons.org/licenses/by-nc/4.0/"},
                            "UsageTerms":{"value":"NonCommercial"}
                          }
                        }]}]}}
                        """);

        ImageSourceValidation result = auditor.validate(candidate());

        assertThat(result.status()).isEqualTo(ImageSourceValidationStatus.RESTRICTED_LICENSE);
        assertThat(result.metadata().licenseLabel()).isEqualTo("CC BY-NC 4.0");
    }

    @Test
    void resolvesOriginalFilenameFromWikimediaThumbnailUrl() {
        ImageAuditCandidate thumbnailCandidate = new ImageAuditCandidate(
                ImageAssetOwnerType.HINT,
                43L,
                "Thumbnail hint",
                "CZ",
                "AI_GENERATED",
                "LANDMARK",
                null,
                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Example.jpg/640px-Example.jpg",
                null,
                null,
                null,
                null,
                null,
                null);
        when(httpClient.get(eq(DataSource.WIKIMEDIA_COMMONS), anyString(), eq(Duration.ofSeconds(20))))
                .thenReturn("""
                        {"query":{"pages":[{"title":"File:Example.jpg","imageinfo":[{
                          "url":"https://upload.wikimedia.org/wikipedia/commons/a/a1/Example.jpg",
                          "descriptionurl":"https://commons.wikimedia.org/wiki/File:Example.jpg",
                          "extmetadata":{
                            "Artist":{"value":"Alice Example"},
                            "LicenseShortName":{"value":"CC BY-SA 4.0"},
                            "LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0/"}
                          }
                        }]}]}}
                        """);

        ImageSourceValidation result = auditor.validate(thumbnailCandidate);

        assertThat(result.status()).isEqualTo(ImageSourceValidationStatus.AVAILABLE);
        verify(httpClient).get(
                eq(DataSource.WIKIMEDIA_COMMONS),
                contains("titles=File%3AExample.jpg"),
                eq(Duration.ofSeconds(20)));
    }

    @Test
    void reportsCommonsApiErrorsAsValidationFailures() {
        when(httpClient.get(eq(DataSource.WIKIMEDIA_COMMONS), anyString(), eq(Duration.ofSeconds(20))))
                .thenReturn("{\"error\":{\"code\":\"readonly\",\"info\":\"Commons is temporarily read-only\"}}");

        ImageSourceValidation result = auditor.validate(candidate());

        assertThat(result.status()).isEqualTo(ImageSourceValidationStatus.FETCH_FAILED);
        assertThat(result.reason()).isEqualTo("Commons is temporarily read-only");
    }

    private ImageAuditCandidate candidate() {
        return new ImageAuditCandidate(
                ImageAssetOwnerType.HINT,
                42L,
                "Example hint",
                "CZ",
                "AI_GENERATED",
                "AI_GENERATED",
                "ai/cz/example.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/a/a1/Example.jpg",
                "https://commons.wikimedia.org/wiki/File:Example.jpg",
                "Alice Example",
                "Alice Example — Own work",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/",
                "https://upload.wikimedia.org/wikipedia/commons/a/a1/Example.jpg");
    }
}
