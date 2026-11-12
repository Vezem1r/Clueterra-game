package io.clueterra.questions.service.source;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.controller.dto.SourceDetail;
import io.clueterra.questions.controller.dto.SourcesOverview;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.hint.lens.CountryHintLens;
import io.clueterra.questions.service.sync.DataSourceClientRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceOverviewServiceTest {

    @Test
    void separatesSyncClientsFromBundledHintSources() {
        DataSourceClientRegistry clientRegistry = mock(DataSourceClientRegistry.class);
        DataSourceClient curatedFactsClient = mock(DataSourceClient.class);
        CountryHintLens riverLens = mock(CountryHintLens.class);

        when(clientRegistry.getClient(DataSource.CURATED_COUNTRY_FACTS)).thenReturn(Optional.of(curatedFactsClient));
        when(clientRegistry.isEnabled(DataSource.CURATED_COUNTRY_FACTS)).thenReturn(true);
        when(clientRegistry.isHealthy(DataSource.CURATED_COUNTRY_FACTS)).thenReturn(true);
        when(curatedFactsClient.getBaseUrl()).thenReturn("classpath:/data/curated-country-facts");
        when(curatedFactsClient.getPriority()).thenReturn(3);
        when(riverLens.source()).thenReturn(DataSource.HYDRO_RIVERS);
        when(riverLens.isAvailable()).thenReturn(true);

        SourcesOverview overview = new SourceOverviewService(clientRegistry, List.of(riverLens), Clock.systemUTC()).getOverview();

        SourceDetail curatedFacts = find(overview, DataSource.CURATED_COUNTRY_FACTS);
        assertThat(curatedFacts.syncAvailable()).isTrue();
        assertThat(curatedFacts.hintGenerationAvailable()).isTrue();

        SourceDetail manual = find(overview, DataSource.MANUAL);
        assertThat(manual.enabled()).isFalse();
        assertThat(manual.syncAvailable()).isFalse();
        assertThat(manual.hintGenerationAvailable()).isFalse();

        SourceDetail rivers = find(overview, DataSource.HYDRO_RIVERS);
        assertThat(rivers.enabled()).isTrue();
        assertThat(rivers.healthy()).isTrue();
        assertThat(rivers.syncAvailable()).isFalse();
        assertThat(rivers.hintGenerationAvailable()).isTrue();

        SourceDetail naturalEarth = find(overview, DataSource.NATURAL_EARTH);
        assertThat(naturalEarth.enabled()).isFalse();
        assertThat(naturalEarth.syncAvailable()).isFalse();
        assertThat(naturalEarth.hintGenerationAvailable()).isFalse();
    }

    @Test
    void reportsConfiguredButUnavailableBundledSourceAsUnhealthy() {
        DataSourceClientRegistry clientRegistry = mock(DataSourceClientRegistry.class);
        CountryHintLens riverLens = mock(CountryHintLens.class);
        when(riverLens.source()).thenReturn(DataSource.HYDRO_RIVERS);
        when(riverLens.isAvailable()).thenReturn(false);

        SourcesOverview overview = new SourceOverviewService(clientRegistry, List.of(riverLens), Clock.systemUTC()).getOverview();

        SourceDetail rivers = find(overview, DataSource.HYDRO_RIVERS);
        assertThat(rivers.enabled()).isTrue();
        assertThat(rivers.healthy()).isFalse();
        assertThat(rivers.status()).isEqualTo("UNHEALTHY");
        assertThat(rivers.hintGenerationAvailable()).isFalse();
    }

    private SourceDetail find(SourcesOverview overview, DataSource source) {
        return overview.sources().stream()
                .filter(detail -> detail.name().equals(source.name()))
                .findFirst()
                .orElseThrow();
    }
}
