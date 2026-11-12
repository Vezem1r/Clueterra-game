package io.clueterra.questions.service.sync;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.AppProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataSourceClientRegistryTest {

    private DataSourceClientRegistry registry;

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    @Test
    void getClient_returnsRegisteredClient() {
        DataSourceClient worldBankClient = client(DataSource.WORLD_BANK, false, true);
        registry = new DataSourceClientRegistry(List.of(worldBankClient), sourcesProperties(Map.of(
                DataSource.WORLD_BANK, true
        )), new AppProperties());

        assertThat(registry.getClient(DataSource.WORLD_BANK)).contains(worldBankClient);
        assertThat(registry.getClient(DataSource.MANUAL)).isEmpty();
    }

    @Test
    void isEnabled_returnsFalseWhenConfigurationIsMissing() {
        registry = new DataSourceClientRegistry(List.of(), new SourcesProperties(), new AppProperties());

        assertThat(registry.isEnabled(DataSource.WORLD_BANK)).isFalse();
    }

    @Test
    void checkAllHealth_populatesHealthCacheForEnabledClientsOnly() {
        DataSourceClient healthy = client(DataSource.WORLD_BANK, false, true);
        DataSourceClient disabled = client(DataSource.WIKIDATA, true, true);
        registry = new DataSourceClientRegistry(List.of(healthy, disabled), sourcesProperties(Map.of(
                DataSource.WORLD_BANK, true,
                DataSource.WIKIDATA, false
        )), new AppProperties());

        registry.checkAllHealth();

        assertThat(registry.isHealthy(DataSource.WORLD_BANK)).isTrue();
        assertThat(registry.isHealthy(DataSource.WIKIDATA)).isFalse();
    }

    @Test
    void cachedHealth_isUnknownUntilFirstCheckAndFalseForDisabledSources() {
        DataSourceClient healthy = client(DataSource.WORLD_BANK, false, true);
        DataSourceClient unhealthy = client(DataSource.CURATED_COUNTRY_FACTS, false, false);
        registry = new DataSourceClientRegistry(List.of(unhealthy, healthy), sourcesProperties(Map.of(
                DataSource.WORLD_BANK, true,
                DataSource.CURATED_COUNTRY_FACTS, true
        )), new AppProperties());

        registry.checkAllHealth();

        assertThat(registry.cachedHealth(DataSource.WORLD_BANK)).contains(true);
        assertThat(registry.cachedHealth(DataSource.CURATED_COUNTRY_FACTS)).contains(false);
        assertThat(registry.cachedHealth(DataSource.WIKIDATA)).contains(false);
        assertThat(registry.enabledClients()).containsExactlyInAnyOrder(healthy, unhealthy);
    }

    private DataSourceClient client(DataSource source, boolean dynamic, boolean available) {
        DataSourceClient client = mock(DataSourceClient.class);
        when(client.getSource()).thenReturn(source);
        when(client.getPriority()).thenReturn(source.getPriority());
        when(client.isDynamic()).thenReturn(dynamic);
        when(client.isAvailable()).thenReturn(available);
        return client;
    }

    private SourcesProperties sourcesProperties(Map<DataSource, Boolean> enabledMap) {
        SourcesProperties properties = new SourcesProperties();
        Map<String, SourceProperties> map = new HashMap<>();
        enabledMap.forEach((source, enabled) -> {
            SourceProperties sourceProperties = new SourceProperties();
            sourceProperties.setEnabled(enabled);
            map.put(source.getConfigKey(), sourceProperties);
        });
        properties.setSources(map);
        return properties;
    }
}
