package io.clueterra.questions.countryphotos.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.countryphotos.model.CommonsFileInfo;
import io.clueterra.questions.countryphotos.model.LandmarkInfo;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CountryLandmarkClient {

    private static final String COMMONS_API = "https://commons.wikimedia.org/w/api.php";
    private static final String WIKIDATA_API = "https://www.wikidata.org/w/api.php";
    private static final String FILE_PREFIX = "File:";
    private static final String MEDIA_INFO_PREFIX = "M";
    private static final String DEPICTS = "P180";
    private static final List<String> LANGUAGES = List.of("en", "ru", "cs");
    private static final int BATCH_SIZE = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClientHelper httpClient;
    private final ObjectMapper objectMapper;

    public Map<String, CommonsFileInfo> files(List<String> titles) {
        Map<String, CommonsFileInfo> result = new HashMap<>();
        batches(titles).forEach(batch -> {
            JsonNode pages = read(httpClient.get(DataSource.WIKIMEDIA_COMMONS, COMMONS_API
                    + "?action=query&format=json&formatversion=2&prop=imageinfo&iiprop=size%7Cmime&titles="
                    + encode(String.join("|", batch)), TIMEOUT)).path("query").path("pages");
            pages.forEach(page -> Optional.ofNullable(page.path("imageinfo").get(0)).ifPresent(info -> {
                String title = page.path("title").asText().substring(FILE_PREFIX.length());
                result.put(title, new CommonsFileInfo(title, page.path("pageid").asLong(),
                        info.path("width").asInt(), info.path("height").asInt(), info.path("mime").asText(null)));
            }));
        });
        return result;
    }

    public Map<Long, String> depictedLandmarks(List<Long> pageIds) {
        Map<Long, String> result = new HashMap<>();
        batches(pageIds).forEach(batch -> {
            String ids = batch.stream().map(id -> MEDIA_INFO_PREFIX + id).collect(Collectors.joining("|"));
            JsonNode entities = read(httpClient.get(DataSource.WIKIMEDIA_COMMONS, COMMONS_API
                    + "?action=wbgetentities&format=json&props=claims&ids=" + encode(ids), TIMEOUT)).path("entities");
            batch.forEach(pageId -> {
                JsonNode entity = entities.path(MEDIA_INFO_PREFIX + pageId);
                JsonNode statements = entity.has("statements") ? entity.path("statements") : entity.path("claims");
                Optional.ofNullable(statements.path(DEPICTS).get(0))
                        .map(statement -> statement.path("mainsnak").path("datavalue").path("value").path("id").asText(null))
                        .filter(qid -> !qid.isBlank())
                        .ifPresent(qid -> result.put(pageId, qid));
            });
        });
        return result;
    }

    public Map<String, LandmarkInfo> landmarks(List<String> qids) {
        Map<String, LandmarkInfo> result = new HashMap<>();
        batches(qids).forEach(batch -> {
            JsonNode entities = read(httpClient.get(DataSource.WIKIDATA, WIKIDATA_API
                    + "?action=wbgetentities&format=json&props=labels%7Csitelinks&languages=" + encode(String.join("|", LANGUAGES))
                    + "&ids=" + encode(String.join("|", batch)), TIMEOUT)).path("entities");
            batch.forEach(qid -> {
                JsonNode entity = entities.path(qid);
                Map<String, String> names = new LinkedHashMap<>();
                LANGUAGES.forEach(language -> Optional.ofNullable(entity.path("labels").path(language).path("value").asText(null))
                        .filter(label -> !label.isBlank())
                        .ifPresent(label -> names.put(language, label)));
                if (names.containsKey(LANGUAGES.getFirst())) {
                    LANGUAGES.forEach(language -> names.putIfAbsent(language, names.get(LANGUAGES.getFirst())));
                    result.put(qid, new LandmarkInfo(qid, Map.copyOf(names), entity.path("sitelinks").size()));
                }
            });
        });
        return result;
    }

    private <T> List<List<T>> batches(List<T> values) {
        List<T> unique = values.stream().distinct().toList();
        List<List<T>> result = new ArrayList<>();
        for (int start = 0; start < unique.size(); start += BATCH_SIZE) {
            result.add(unique.subList(start, Math.min(unique.size(), start + BATCH_SIZE)));
        }
        return result;
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Wikimedia returned invalid JSON", error);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
