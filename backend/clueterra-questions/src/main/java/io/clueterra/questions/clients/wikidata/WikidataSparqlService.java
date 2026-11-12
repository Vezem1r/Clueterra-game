package io.clueterra.questions.clients.wikidata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.DataSourceException;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WikidataSparqlService {

    private final HttpClientHelper httpClientHelper;
    private final ObjectMapper objectMapper;

    public JsonNode executeQuery(String baseUrl, String sparql, Duration timeout) {
        String encodedQuery = URLEncoder.encode(sparql, StandardCharsets.UTF_8);
        String url = baseUrl + "?query=" + encodedQuery + "&format=json";

        try {
            return httpClientHelper.getOptional(DataSource.WIKIDATA, url, timeout)
                    .map(responseBody -> {
                        try {
                            JsonNode root = objectMapper.readTree(responseBody);
                            return root.path("results").path("bindings");
                        } catch (Exception e) {
                            throw new DataSourceException(DataSource.WIKIDATA, "Failed to parse SPARQL response from " + url, e);
                        }
                    })
                    .orElseGet(objectMapper::createArrayNode);

        } catch (DataSourceException e) {
            throw e;
        } catch (Exception e) {
            throw new DataSourceException(DataSource.WIKIDATA, "SPARQL execution failed at " + url + ": " + e.getMessage(), e);
        }
    }

    public String getCountryQid(String baseUrl, String isoCode, Duration timeout) {
        if (!StringUtils.hasText(isoCode)) {
            return null;
        }

        String query = String.format("SELECT ?country WHERE { ?country wdt:P297 \"%s\". } LIMIT 1", isoCode.toUpperCase());

        try {
            JsonNode bindings = executeQuery(baseUrl, query, timeout);
            if (bindings.isEmpty()) {
                return null;
            }
            String uri = bindings.get(0).path("country").path("value").asText();
            return uri.substring(uri.lastIndexOf('/') + 1);
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, String> getAllCountryQids(String baseUrl, Duration timeout) {
        Map<String, String> map = new HashMap<>();
        String query = "SELECT ?code (SAMPLE(?item) AS ?country) WHERE { "
                + "?item wdt:P297 ?code. FILTER(STRLEN(?code) = 2) } GROUP BY ?code";

        JsonNode bindings = executeQuery(baseUrl, query, timeout);

        for (JsonNode binding : bindings) {
            if (binding.has("code") && binding.has("country")) {
                String code = binding.get("code").path("value").asText();
                String uri = binding.get("country").path("value").asText();
                if (StringUtils.hasText(code) && StringUtils.hasText(uri)) {
                    String qid = uri.substring(uri.lastIndexOf('/') + 1);
                    map.put(code.toUpperCase(), qid);
                }
            }
        }
        return map;
    }
}
