package io.clueterra.questions.clients.wikidata;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.wikidata.dto.WikidataMetric;
import io.clueterra.questions.entity.enums.DataSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikidataStatementServiceTest {

    @Test
    void resolvesCurrentPreferredStatementsAndDropsEndedOrDeprecatedValues() {
        HttpClientHelper httpClient = mock(HttpClientHelper.class);
        Duration timeout = Duration.ofSeconds(10);
        String restUrl = "https://www.wikidata.org/w/rest.php/wikibase/v1/entities/items/Q183/statements";
        String actionUrl = "https://www.wikidata.org/w/api.php";
        when(httpClient.get(DataSource.WIKIDATA, restUrl, timeout, Map.of("Accept", "application/json")))
                .thenReturn(statementsJson());
        when(httpClient.get(
                eq(DataSource.WIKIDATA),
                startsWith(actionUrl + "?action=wbgetentities"),
                eq(timeout),
                anyMap()))
                .thenReturn(labelsJson());

        WikidataStatementService service = new WikidataStatementService(httpClient, new ObjectMapper());
        Map<WikidataMetric, List<String>> values = service.fetchAllValues(
                "https://www.wikidata.org/w/rest.php/wikibase/v1", actionUrl, "Q183", timeout);

        assertThat(values.get(WikidataMetric.NATIONAL_ANTHEM)).containsExactly("Current Anthem");
        assertThat(values.get(WikidataMetric.CALLING_CODE)).containsExactly("+49");
        assertThat(values.get(WikidataMetric.TOP_LEVEL_DOMAIN)).containsExactly(".de");
        assertThat(values.get(WikidataMetric.DRIVING_SIDE)).isEmpty();
        assertThat(values.get(WikidataMetric.ELECTRICAL_PLUG)).containsExactly("Schuko");
        verify(httpClient).get(DataSource.WIKIDATA, restUrl, timeout, Map.of("Accept", "application/json"));
    }

    @Test
    void resolvesLargeLabelSetsInApiSafeBatches() {
        HttpClientHelper httpClient = mock(HttpClientHelper.class);
        Duration timeout = Duration.ofSeconds(10);
        String actionUrl = "https://www.wikidata.org/w/api.php";
        Set<String> itemIds = IntStream.rangeClosed(1, 41)
                .mapToObj(index -> "Q" + index)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        when(httpClient.get(
                eq(DataSource.WIKIDATA),
                startsWith(actionUrl + "?action=wbgetentities"),
                eq(timeout),
                anyMap()))
                .thenReturn(labelsJson(41));

        WikidataStatementService service = new WikidataStatementService(httpClient, new ObjectMapper());
        Map<String, String> labels = service.resolveEnglishLabels(actionUrl, itemIds, timeout);

        assertThat(labels).hasSize(41);
        assertThat(labels.get("Q1")).isEqualTo("Label 1");
        assertThat(labels.get("Q41")).isEqualTo("Label 41");
        verify(httpClient, times(2)).get(
                eq(DataSource.WIKIDATA),
                startsWith(actionUrl + "?action=wbgetentities"),
                eq(timeout),
                anyMap());
    }

    private String statementsJson() {
        return """
                {
                  "P85": [
                    {"rank":"normal","qualifiers":[{"property":{"id":"P582"}}],"value":{"type":"value","content":"QOLD"}},
                    {"rank":"normal","qualifiers":[],"value":{"type":"value","content":"QNORMAL"}},
                    {"rank":"preferred","qualifiers":[],"value":{"type":"value","content":"QCURRENT"}}
                  ],
                  "P474": [
                    {"rank":"normal","qualifiers":[],"value":{"type":"value","content":"+49"}},
                    {"rank":"deprecated","qualifiers":[],"value":{"type":"value","content":"+00"}}
                  ],
                  "P78": [{"rank":"normal","qualifiers":[],"value":{"type":"value","content":"QDOMAIN"}}],
                  "P1622": [{"rank":"normal","qualifiers":[{"property":{"id":"P582"}}],"value":{"type":"value","content":"QRIGHT"}}],
                  "P2853": [{"rank":"normal","qualifiers":[],"value":{"type":"value","content":"QPLUG"}}]
                }
                """;
    }

    private String labelsJson() {
        return """
                {"entities":{
                  "QCURRENT":{"labels":{"en":{"value":"Current Anthem"}}},
                  "QNORMAL":{"labels":{"en":{"value":"Normal Anthem"}}},
                  "QDOMAIN":{"labels":{"en":{"value":".de"}}},
                  "QPLUG":{"labels":{"en":{"value":"Schuko"}}}
                }}
                """;
    }

    private String labelsJson(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> "\"Q%d\":{\"labels\":{\"en\":{\"value\":\"Label %d\"}}}"
                        .formatted(index, index))
                .collect(Collectors.joining(",", "{\"entities\":{", "}}"));
    }
}
