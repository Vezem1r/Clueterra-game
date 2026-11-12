package io.clueterra.questions.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.ai.service.generation.AiResolvedWikimediaImage;
import io.clueterra.questions.ai.service.generation.AiWikimediaImageResolver;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiWikimediaImageResolverTest {

    private static final String COMMONS_RESPONSE = """
            {"query":{"pages":[{
              "title":"File:Mir Castle.jpg",
              "imageinfo":[{
                "url":"https://upload.wikimedia.org/wikipedia/commons/b/b4/Mir_Castle.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original",
                "descriptionurl":"https://commons.wikimedia.org/wiki/File:Mir_Castle.jpg",
                "extmetadata":{
                  "LicenseShortName":{"value":"CC BY-SA 4.0"},
                  "LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0"},
                  "Artist":{"value":"Jane Doe"}
                }
              }]
            }]}}
            """;

    private final HttpClientHelper httpClient = mock(HttpClientHelper.class);
    private final SourcesProperties sourcesProperties = sourcesProperties();
    private final AiWikimediaImageResolver resolver =
            new AiWikimediaImageResolver(httpClient, new ObjectMapper(), sourcesProperties);

    @Test
    void resolvesImageWhenCommonsAppendsTrackingParameters() {
        when(httpClient.get(any(DataSource.class), anyString(), any(Duration.class)))
                .thenReturn(COMMONS_RESPONSE);

        Optional<AiResolvedWikimediaImage> resolved = resolver.search("Mir Castle Belarus");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().imageUrl())
                .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/b/b4/Mir_Castle.jpg");
    }

    @Test
    void skipsUnsupportedFileTypesOnceTrackingParametersAreStripped() {
        when(httpClient.get(any(DataSource.class), anyString(), any(Duration.class)))
                .thenReturn(COMMONS_RESPONSE.replace("Mir_Castle.jpg?utm_source", "Mir_Castle.svg?utm_source"));

        assertThat(resolver.search("Mir Castle Belarus")).isEmpty();
    }

    @Test
    void resolveImageFallsBackToSearchTermsWhenExactLookupMisses() {
        when(httpClient.get(any(DataSource.class), anyString(), any(Duration.class)))
                .thenReturn("{\"query\":{\"pages\":[{\"missing\":true}]}}", COMMONS_RESPONSE);

        Optional<AiResolvedWikimediaImage> resolved = resolver.resolveImage(
                "https://commons.wikimedia.org/wiki/File:Absent.jpg", List.of("Mir Castle Belarus"));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().licenseLabel()).isEqualTo("CC BY-SA 4.0");
    }

    @Test
    void publicDomainSearchSkipsAttributionOnlyCurrencyFiles() {
        when(httpClient.get(any(DataSource.class), anyString(), any(Duration.class)))
                .thenReturn(COMMONS_RESPONSE);

        assertThat(resolver.searchPublicDomain("historic banknote")).isEmpty();
    }

    @Test
    void publicDomainSearchAcceptsExplicitPublicDomainMetadata() {
        when(httpClient.get(any(DataSource.class), anyString(), any(Duration.class)))
                .thenReturn(COMMONS_RESPONSE
                        .replace("CC BY-SA 4.0", "Public domain")
                        .replace("https://creativecommons.org/licenses/by-sa/4.0", "https://creativecommons.org/publicdomain/mark/1.0/"));

        assertThat(resolver.searchPublicDomain("historic banknote")).isPresent();
    }

    private static SourcesProperties sourcesProperties() {
        SourceProperties source = new SourceProperties();
        source.setTimeoutSeconds(15);
        source.setRateLimitPerSecond(1);
        SourcesProperties sources = new SourcesProperties();
        sources.getSources().put("wikimedia-commons", source);
        return sources;
    }
}
