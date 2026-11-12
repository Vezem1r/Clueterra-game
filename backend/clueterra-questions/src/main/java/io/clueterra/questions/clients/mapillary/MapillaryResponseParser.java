package io.clueterra.questions.clients.mapillary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.mapillary.dto.MapillaryImageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MapillaryResponseParser {

    private final ObjectMapper objectMapper;

    public List<MapillaryImageResult> parse(String json) {
        List<MapillaryImageResult> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return results;
            }

            for (JsonNode node : data) {
                parseNode(node).ifPresent(results::add);
            }
        } catch (Exception e) {
            log.warn("Failed to parse Mapillary response", e);
        }
        return results;
    }

    private Optional<MapillaryImageResult> parseNode(JsonNode node) {
        Optional<String> originalUrl = MapillaryPanoramaQuality.usableOriginalUrl(node);
        if (originalUrl.isEmpty()) {
            return Optional.empty();
        }

        JsonNode coordinates = node.path("geometry").path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2) {
            return Optional.empty();
        }

        return Optional.of(new MapillaryImageResult(
                node.path("id").asLong(),
                coordinates.get(1).asDouble(),
                coordinates.get(0).asDouble(),
                originalUrl.get(),
                node.path("quality_score").asDouble(),
                node.path("captured_at").asLong(0),
                node.path("creator").path("username").asText(null),
                node.path("sequence").asText(null)
        ));
    }
}
