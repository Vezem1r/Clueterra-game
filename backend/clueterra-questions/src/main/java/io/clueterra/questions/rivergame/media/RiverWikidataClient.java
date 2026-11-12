package io.clueterra.questions.rivergame.media;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RiverWikidataClient {

    private static final String SPARQL_ENDPOINT = "https://query.wikidata.org/sparql";
    private static final String ENTITY_PREFIX = "http://www.wikidata.org/entity/";
    private static final String FILE_PATH_MARKER = "/Special:FilePath/";
    private static final String WIKIPEDIA_PREFIX = "https://en.wikipedia.org/wiki/";
    private static final int BATCH_SIZE = 80;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Map<String, String> HEADERS = Map.of("Accept", "application/sparql-results+json");

    private final HttpClientHelper httpClient;
    private final ObjectMapper objectMapper;

    public List<RiverWikidataRecord> fetch(List<String> riverKeys) {
        List<RiverWikidataRecord> records = new ArrayList<>(riverKeys.size());
        for (int start = 0; start < riverKeys.size(); start += BATCH_SIZE) {
            records.addAll(fetchBatch(riverKeys.subList(start, Math.min(riverKeys.size(), start + BATCH_SIZE))));
        }
        return records;
    }

    private List<RiverWikidataRecord> fetchBatch(List<String> riverKeys) {
        String values = riverKeys.stream()
                .map(key -> "wd:" + key.toUpperCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
        String query = "SELECT ?river ?image ?article WHERE { VALUES ?river { " + values + " } "
                + "OPTIONAL { ?river wdt:P18 ?image } "
                + "OPTIONAL { ?article schema:about ?river ; schema:isPartOf <https://en.wikipedia.org/> } }";
        String body = httpClient.get(DataSource.WIKIDATA,
                SPARQL_ENDPOINT + "?format=json&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8),
                TIMEOUT, HEADERS);
        Map<String, Set<String>> files = new LinkedHashMap<>();
        Map<String, String> articles = new LinkedHashMap<>();
        riverKeys.forEach(key -> files.put(key, new LinkedHashSet<>()));
        bindings(body).forEach(binding -> {
            String riverKey = value(binding, "river")
                    .filter(uri -> uri.startsWith(ENTITY_PREFIX))
                    .map(uri -> uri.substring(ENTITY_PREFIX.length()).toLowerCase(Locale.ROOT))
                    .filter(files::containsKey)
                    .orElse(null);
            if (riverKey != null) {
                value(binding, "image").flatMap(this::commonsTitle).ifPresent(files.get(riverKey)::add);
                value(binding, "article").filter(url -> url.startsWith(WIKIPEDIA_PREFIX))
                        .ifPresent(url -> articles.putIfAbsent(riverKey, url));
            }
        });
        return riverKeys.stream()
                .map(key -> new RiverWikidataRecord(key, articles.get(key), List.copyOf(files.get(key))))
                .toList();
    }

    private List<JsonNode> bindings(String body) {
        try {
            JsonNode bindings = objectMapper.readTree(body).path("results").path("bindings");
            if (!bindings.isArray()) {
                throw new IllegalStateException("Wikidata returned no SPARQL bindings");
            }
            List<JsonNode> result = new ArrayList<>();
            bindings.forEach(result::add);
            return result;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Wikidata returned invalid SPARQL JSON", error);
        }
    }

    private Optional<String> value(JsonNode binding, String field) {
        return Optional.ofNullable(binding.path(field).path("value").asText(null))
                .filter(value -> !value.isBlank());
    }

    private Optional<String> commonsTitle(String filePathUrl) {
        int marker = filePathUrl.indexOf(FILE_PATH_MARKER);
        return marker < 0 ? Optional.empty()
                : Optional.of(URLDecoder.decode(filePathUrl.substring(marker + FILE_PATH_MARKER.length())
                        .replace("+", "%2B"), StandardCharsets.UTF_8).replace('_', ' '))
                .filter(title -> !title.isBlank());
    }
}
