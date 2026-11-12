package io.clueterra.questions.service.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeJsonCodecTest {

    private final ChallengeJsonCodec codec = new ChallengeJsonCodec(new ObjectMapper());

    @Test
    void escapesControlCharactersAndRoundTrips() {
        ChallengeHintEntry hint = new ChallengeHintEntry(1, 1, 7L, "Line\u0001 \"quoted\"\nnext", 40,
                "CULTURE", "CULTURAL", "MANUAL", null, null, null, null, null, null, null, null);

        String json = codec.writeHints(List.of(hint));

        assertThat(json).doesNotContain("imageUrl").doesNotContain("\u0001");
        assertThat(codec.readHints(json)).containsExactly(hint);
    }

    @Test
    void readsLegacyJsonWrittenByTheOldGenerator() {
        String legacy = "[{\"countryOrder\":2,\"hintOrder\":1,\"hintId\":9,\"text\":\"t\",\"difficulty\":30,"
                + "\"category\":\"GEOGRAPHY\",\"type\":\"ABSOLUTE_FACT\",\"source\":\"CIA_FACTBOOK\","
                + "\"imageUrl\":\"hints/x.jpg\",\"unknownField\":true}]";

        List<ChallengeHintEntry> hints = codec.readHints(legacy);

        assertThat(hints).singleElement().satisfies(h -> {
            assertThat(h.hintId()).isEqualTo(9L);
            assertThat(h.imageUrl()).isEqualTo("hints/x.jpg");
        });
    }

    @Test
    void writesEntriesSortedByOrder() {
        String json = codec.writeCountries(List.of(
                new ChallengeCountryEntry(2, 20L, "FR", "France"),
                new ChallengeCountryEntry(1, 10L, "DE", "Germany")));

        assertThat(codec.readCountries(json)).extracting(ChallengeCountryEntry::code).containsExactly("DE", "FR");
    }
}
