package io.clueterra.api.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.game.dto.HintDto;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class ChallengeHintParserTest {

    private ChallengeHintParser parser;

    private static final String HINTS_JSON = """
            [
              {"countryOrder":1,"hintOrder":1,"hintId":101,"text":"Located in Europe",
               "difficulty":1,"category":"geography","type":"text","source":"wiki"},
              {"countryOrder":1,"hintOrder":2,"hintId":102,"text":"Has Alps mountains",
               "difficulty":2,"category":"nature","type":"text","source":"wiki"},
              {"countryOrder":1,"hintOrder":3,"hintId":103,"text":"Famous for chocolate",
               "difficulty":3,"category":"culture","type":"text","source":"wiki"},
              {"countryOrder":2,"hintOrder":1,"hintId":201,"text":"Island nation",
               "difficulty":1,"category":"geography","type":"text","source":"wiki"},
              {"countryOrder":2,"hintOrder":2,"hintId":202,"text":"Known for kangaroos",
               "difficulty":2,"category":"nature","type":"text","source":"wiki"},
              {"countryOrder":2,"hintOrder":3,"hintId":203,"text":"Has the Great Barrier Reef",
               "difficulty":1,"category":"nature","type":"text","source":"wiki"}
            ]
            """;

    private static final String COUNTRIES_JSON = """
            [{"countryId":42,"order":1},{"countryId":99,"order":2}]
            """;

    @BeforeEach
    void setUp() {
        var storageService = Mockito.mock(io.clueterra.api.storage.StorageService.class);
        Mockito.when(storageService.resolveHintAssetUrl(ArgumentMatchers.anyLong(), ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(1));
        parser = new ChallengeHintParser(new ObjectMapper(), storageService);
    }

    @Nested
    @DisplayName("countHintsPerCountry")
    class CountHintsPerCountry {

        @Test
        @DisplayName("returns the max hintOrder across all countries (= hints per country)")
        void returnsMaxHintOrder() {
            assertThat(parser.countHintsPerCountry(HINTS_JSON)).isEqualTo(3);
        }

        @Test
        @DisplayName("returns 1 when every country has exactly one hint")
        void returnsOneForSingleHintPerCountry() {
            String json = """
                    [
                      {"countryOrder":1,"hintOrder":1,"hintId":1,"text":"A",
                       "difficulty":1,"category":"geo","type":"text","source":"x"},
                      {"countryOrder":2,"hintOrder":1,"hintId":2,"text":"B",
                       "difficulty":1,"category":"geo","type":"text","source":"x"}
                    ]
                    """;
            assertThat(parser.countHintsPerCountry(json)).isEqualTo(1);
        }

        @Test
        @DisplayName("returns 0 for empty hints array")
        void returnsZeroForEmptyArray() {
            assertThat(parser.countHintsPerCountry("[]")).isZero();
        }

        @Test
        @DisplayName("returns 0 and does not throw for completely malformed JSON")
        void returnsZeroForMalformedJson() {
            assertThat(parser.countHintsPerCountry("{broken")).isZero();
        }

        @Test
        @DisplayName("returns 0 for null-like empty string")
        void returnsZeroForEmptyString() {
            assertThat(parser.countHintsPerCountry("")).isZero();
        }
    }

    @Test
    @DisplayName("countHintsByCountryOrder returns exact counts for each country")
    void countHintsByCountryOrder_returnsPerCountryCounts() {
        assertThat(parser.countHintsByCountryOrder(HINTS_JSON))
                .isEqualTo(Map.of(1, 3, 2, 3));
    }

    @Nested
    @DisplayName("getHintAtPosition")
    class GetHintAtPosition {

        @Test
        @DisplayName("returns the correct hint for country 1 at position 1")
        void returnsFirstHintForFirstCountry() {
            HintDto hint = parser.getHintAtPosition(HINTS_JSON, 1, 1);
            assertThat(hint).isNotNull();
            assertThat(hint.text()).isEqualTo("Located in Europe");
            assertThat(hint.difficulty()).isEqualTo(1);
            assertThat(hint.category()).isEqualTo("geography");
        }

        @Test
        @DisplayName("returns the correct hint for country 1 at position 2")
        void returnsMidHintForFirstCountry() {
            HintDto hint = parser.getHintAtPosition(HINTS_JSON, 1, 2);
            assertThat(hint).isNotNull();
            assertThat(hint.text()).isEqualTo("Has Alps mountains");
        }

        @Test
        @DisplayName("returns hint for second country at position 3")
        void returnsLastHintForSecondCountry() {
            HintDto hint = parser.getHintAtPosition(HINTS_JSON, 2, 3);
            assertThat(hint).isNotNull();
            assertThat(hint.text()).isEqualTo("Has the Great Barrier Reef");
        }

        @Test
        @DisplayName("returns null when hint position does not exist")
        void returnsNullForOutOfBoundsPosition() {
            assertThat(parser.getHintAtPosition(HINTS_JSON, 1, 99)).isNull();
        }

        @Test
        @DisplayName("returns null when country order does not exist in JSON")
        void returnsNullForUnknownCountryOrder() {
            assertThat(parser.getHintAtPosition(HINTS_JSON, 99, 1)).isNull();
        }
    }

    @Nested
    @DisplayName("getHintsForCountry")
    class GetHintsForCountry {

        @Test
        @DisplayName("returns only hints with hintOrder <= revealedCount for the given country")
        void returnsUpToRevealedCount() {
            List<HintDto> hints = parser.getHintsForCountry(HINTS_JSON, 1, 2);
            assertThat(hints).hasSize(2);
            assertThat(hints).extracting(HintDto::text)
                    .containsExactlyInAnyOrder("Located in Europe", "Has Alps mountains");
        }

        @Test
        @DisplayName("returns all hints when revealedCount equals total hints")
        void returnsAllHintsWhenFullyRevealed() {
            assertThat(parser.getHintsForCountry(HINTS_JSON, 1, 3)).hasSize(3);
        }

        @Test
        @DisplayName("returns empty list when revealedCount is 0")
        void returnsEmptyWhenNothingRevealed() {
            // hint orders start at 1, so hintOrder <= 0 is never true
            assertThat(parser.getHintsForCountry(HINTS_JSON, 1, 0)).isEmpty();
        }

        @Test
        @DisplayName("never includes hints from other countries")
        void doesNotCrossCountryBoundaries() {
            List<HintDto> country1 = parser.getHintsForCountry(HINTS_JSON, 1, 3);
            List<HintDto> country2 = parser.getHintsForCountry(HINTS_JSON, 2, 3);
            assertThat(country1).isNotEmpty().extracting(HintDto::text).doesNotContain("Island nation", "Known for kangaroos");
            assertThat(country2).isNotEmpty().extracting(HintDto::text).doesNotContain("Located in Europe", "Has Alps mountains");
        }
    }

    @Nested
    @DisplayName("parseCountryEntries")
    class ParseCountryEntries {

        @Test
        @DisplayName("correctly maps countryId and order from JSON")
        void mapsFieldsCorrectly() {
            List<ChallengeHintParser.CountryEntry> entries = parser.parseCountryEntries(COUNTRIES_JSON);
            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).countryId()).isEqualTo(42L);
            assertThat(entries.get(0).order()).isEqualTo(1);
            assertThat(entries.get(1).countryId()).isEqualTo(99L);
            assertThat(entries.get(1).order()).isEqualTo(2);
        }

        @Test
        @DisplayName("preserves insertion order")
        void preservesOrder() {
            List<ChallengeHintParser.CountryEntry> entries = parser.parseCountryEntries(COUNTRIES_JSON);
            assertThat(entries).extracting(ChallengeHintParser.CountryEntry::order)
                    .containsExactly(1, 2);
        }
    }

    @Test
    @DisplayName("parseSessionCountries merges country order with per-country hint totals")
    void parseSessionCountries_includesHintTotals() {
        List<ChallengeHintParser.SessionCountrySeed> entries = parser.parseSessionCountries(COUNTRIES_JSON, HINTS_JSON);
        assertThat(entries).hasSize(2);
        assertThat(entries.getFirst().countryId()).isEqualTo(42L);
        assertThat(entries.get(0).order()).isEqualTo(1);
        assertThat(entries.get(0).totalHints()).isEqualTo(3);
        assertThat(entries.get(1).countryId()).isEqualTo(99L);
        assertThat(entries.get(1).order()).isEqualTo(2);
        assertThat(entries.get(1).totalHints()).isEqualTo(3);
    }

    @Test
    @DisplayName("getHintAtPosition maps image attribution metadata when present")
    void getHintAtPosition_mapsImageAttributionMetadata() {
        String json = """
                [
                  {"countryOrder":1,"hintOrder":1,"hintId":301,"text":"Street-level image",
                   "difficulty":12,"category":"geography","type":"PANORAMA","source":"MAPILLARY",
                   "imageUrl":"https://cdn.example.com/hints/mapillary.jpg",
                   "imageSourcePageUrl":"https://www.mapillary.com/app/?pKey=123&focus=photo",
                   "imageAttributionText":"Street-level imagery via Mapillary.",
                   "imageLicenseLabel":"CC BY-SA",
                   "imageLicenseUrl":"https://help.mapillary.com/hc/en-us/articles/115001770409-CC-BY-SA-license-for-open-data"}
                ]
                """;

        HintDto hint = parser.getHintAtPosition(json, 1, 1);

        assertThat(hint).isNotNull();
        assertThat(hint.imageSourcePageUrl()).isEqualTo("https://www.mapillary.com/app/?pKey=123&focus=photo");
        assertThat(hint.imageAttributionText()).isEqualTo("Street-level imagery via Mapillary.");
        assertThat(hint.imageLicenseLabel()).isEqualTo("CC BY-SA");
        assertThat(hint.imageLicenseUrl())
                .isEqualTo("https://help.mapillary.com/hc/en-us/articles/115001770409-CC-BY-SA-license-for-open-data");
    }
}
