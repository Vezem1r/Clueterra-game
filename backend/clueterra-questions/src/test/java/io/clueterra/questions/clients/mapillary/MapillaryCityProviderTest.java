package io.clueterra.questions.clients.mapillary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapillaryCityProviderTest {

    @Test
    void loadsGlobalLandmarkSeedsWithoutReplacingCityCoverage() throws Exception {
        MapillaryCityProvider provider = new MapillaryCityProvider();
        provider.init();

        assertThat(provider.getLandmarks("US")).hasSize(18);
        assertThat(provider.getLandmarks("FR")).hasSize(18);
        assertThat(provider.getLandmarks("US").getFirst()).containsExactly(40.758, -73.9855);
        assertThat(provider.getLandmarks("FR").getFirst()).containsExactly(48.8584, 2.2945);
        assertThat(provider.getLandmarks("CZ")).isNotEmpty();
        assertThat(provider.getCities("US")).isNotEmpty();
        assertThat(provider.getRural("US")).isNotEmpty();
    }
}
