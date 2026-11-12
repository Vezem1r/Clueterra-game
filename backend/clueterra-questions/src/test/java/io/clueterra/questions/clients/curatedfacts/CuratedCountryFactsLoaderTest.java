package io.clueterra.questions.clients.curatedfacts;

import io.clueterra.questions.clients.common.CountryReference;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CuratedCountryFactsLoaderTest {

    private final CuratedCountryFactsLoader loader = new CuratedCountryFactsLoader();

    @Test
    void everyBundledCollectionLoadsWithKnownCountryCodesAndUsefulValues() {
        for (CuratedCountryFactIndicator indicator : CuratedCountryFactIndicator.values()) {
            Map<String, String> facts = loader.load(indicator.getFileName());

            assertThat(facts)
                    .as(indicator.getFileName())
                    .isNotEmpty()
                    .allSatisfy((countryCode, value) -> {
                        assertThat(CountryReference.getCountryName(countryCode)).isNotBlank();
                        assertThat(value).isNotBlank();
                    });
        }
    }
}
