package io.clueterra.questions.clients.ciafactbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CIAFactbookClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deriveCountryName_prefersConventionalShortForm() throws Exception {
        String json = """
                {
                  "Government": {
                    "Country name": {
                      "conventional short form": "Japan",
                      "conventional long form": "Japan State"
                    }
                  }
                }
                """;

        String name = CIAFactbookClient.deriveCountryName(objectMapper.readTree(json), "JP");

        assertThat(name).isEqualTo("Japan");
    }

    @Test
    void deriveCountryName_fallsBackToIsoDisplayNameWhenJsonNameIsMissing() throws Exception {
        String json = """
                {
                  "Government": {
                    "Country name": {}
                  }
                }
                """;

        String name = CIAFactbookClient.deriveCountryName(objectMapper.readTree(json), "JP");

        assertThat(name).isEqualTo("Japan");
    }
}
