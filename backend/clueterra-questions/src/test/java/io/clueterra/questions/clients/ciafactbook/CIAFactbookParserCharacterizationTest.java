package io.clueterra.questions.clients.ciafactbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CIAFactbookParserCharacterizationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CIAFactbookParser parser = new CIAFactbookParser();

    @Test
    @DisplayName("CIA parser extracts border and coastline facts from geography payloads")
    void parse_geography_extracts_border_and_coastline_facts() throws Exception {
        String json = """
                {
                  "Geography": {
                    "Land boundaries": {
                      "total": "4,301 km",
                      "border countries": "Belarus 1312 km; Lithuania 261 km; Poland 418 km"
                    },
                    "Coastline": "0 km (landlocked)"
                  }
                }
                """;

        List<RawStatistic> stats = parser.parseGeography(objectMapper.readTree(json));

        assertThat(findText(stats, "BORDER_COUNTRIES"))
                .isEqualTo("Belarus 1312 km; Lithuania 261 km; Poland 418 km");
        assertThat(findValue(stats, "LAND_BOUNDARIES_TOTAL")).isEqualByComparingTo("4301.00");
        assertThat(findValue(stats, "COASTLINE")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("CIA parser cleans HTML and nbsp before saving textual facts")
    void parse_all_cleans_html_and_nbsp_sequences() throws Exception {
        String json = """
                {
                  "Geography": {
                    "Climate": {
                      "text": "temperate<br/>and&nbsp;windy"
                    }
                  }
                }
                """;

        List<RawStatistic> stats = parser.parseAll(objectMapper.readTree(json));

        assertThat(findText(stats, "CLIMATE")).isEqualTo("temperate and windy");
    }

    @Test
    @DisplayName("CIA parser extracts capital and country etymology from government payloads")
    void parse_government_extracts_capital_and_etymology_fields() throws Exception {
        String json = """
                {
                  "Government": {
                    "Country name": {
                      "etymology": "named after the river that crosses the region"
                    },
                    "Capital": {
                      "name": "Lusaka",
                      "etymology": "named after a village chief"
                    }
                  }
                }
                """;

        List<RawStatistic> stats = parser.parseGovernment(objectMapper.readTree(json));

        assertThat(findText(stats, "NAME_ETYMOLOGY"))
                .isEqualTo("named after the river that crosses the region");
        assertThat(findText(stats, "CAPITAL_CITY")).isEqualTo("Lusaka");
        assertThat(findText(stats, "CAPITAL_ETYMOLOGY")).isEqualTo("named after a village chief");
    }

    @Test
    @DisplayName("CIA parser keeps the text-length guardrail for oversized government facts")
    void parse_government_skips_texts_above_the_guardrail() throws Exception {
        String oversized = "x".repeat(401);
        String json = """
                {
                  "Government": {
                    "Government type": {
                      "text": "federal republic"
                    },
                    "Flag": {
                      "text": "%s"
                    }
                  }
                }
                """.formatted(oversized);

        List<RawStatistic> stats = parser.parseGovernment(objectMapper.readTree(json));

        assertThat(stats)
                .isNotEmpty()
                .extracting(RawStatistic::indicatorCode)
                .doesNotContain("FLAG_DESCRIPTION");
    }

    private String findText(List<RawStatistic> stats, String indicatorCode) {
        return stats.stream()
                .filter(stat -> indicatorCode.equals(stat.indicatorCode()))
                .map(RawStatistic::valueText)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal findValue(List<RawStatistic> stats, String indicatorCode) {
        return stats.stream()
                .filter(stat -> indicatorCode.equals(stat.indicatorCode()))
                .map(RawStatistic::value)
                .findFirst()
                .orElse(null);
    }
}
