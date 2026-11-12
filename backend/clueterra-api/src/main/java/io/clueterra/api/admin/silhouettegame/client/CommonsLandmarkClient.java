package io.clueterra.api.admin.silhouettegame.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.silhouettegame.model.SilhouetteLandmark;
import io.clueterra.api.admin.hints.image.WikimediaImageException;

@Component
public class CommonsLandmarkClient {

    private static final String COMMONS_API = "https://commons.wikimedia.org/w/api.php";
    private static final String WIKIDATA_API = "https://www.wikidata.org/w/api.php";
    private static final String USER_AGENT = "Clueterra/1.0 (admin image maintenance)";
    private static final String FILE_PREFIX = "File:";
    private static final String DEPICTS = "P180";
    private static final List<String> LANGUAGES = List.of("en", "ru", "cs");
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CommonsLandmarkClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Optional<String> depicted(String fileTitle) {
        URI uri = UriComponentsBuilder.fromUriString(COMMONS_API)
                .queryParam("action", "wbgetentities")
                .queryParam("format", "json")
                .queryParam("sites", "commonswiki")
                .queryParam("props", "claims")
                .queryParam("titles", FILE_PREFIX + fileTitle)
                .build()
                .encode()
                .toUri();
        JsonNode entities = read(uri).path("entities");
        return Optional.ofNullable(entities.elements().hasNext() ? entities.elements().next() : null)
                .map(entity -> entity.has("statements") ? entity.path("statements") : entity.path("claims"))
                .map(statements -> statements.path(DEPICTS).path(0)
                        .path("mainsnak").path("datavalue").path("value").path("id").asText(""))
                .filter(qid -> !qid.isBlank());
    }

    public Optional<SilhouetteLandmark> landmark(String qid) {
        URI uri = UriComponentsBuilder.fromUriString(WIKIDATA_API)
                .queryParam("action", "wbgetentities")
                .queryParam("format", "json")
                .queryParam("props", "labels|sitelinks")
                .queryParam("languages", String.join("|", LANGUAGES))
                .queryParam("ids", qid)
                .build()
                .encode()
                .toUri();
        JsonNode entity = read(uri).path("entities").path(qid);
        Map<String, String> names = new LinkedHashMap<>();
        LANGUAGES.forEach(language -> Optional.of(entity.path("labels").path(language).path("value").asText(""))
                .filter(label -> !label.isBlank())
                .ifPresent(label -> names.put(language, label)));
        return Optional.ofNullable(names.get(LANGUAGES.getFirst())).map(english -> {
            LANGUAGES.forEach(language -> names.putIfAbsent(language, english));
            return new SilhouetteLandmark(qid, Map.copyOf(names), entity.path("sitelinks").size());
        });
    }

    private JsonNode read(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new WikimediaImageException(HttpStatus.BAD_GATEWAY,
                        "Wikimedia landmark lookup failed with HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WikimediaImageException(HttpStatus.BAD_GATEWAY, "Wikimedia landmark lookup was interrupted", exception);
        } catch (IOException exception) {
            throw new WikimediaImageException(HttpStatus.BAD_GATEWAY, "Wikimedia landmark lookup failed", exception);
        }
    }
}
