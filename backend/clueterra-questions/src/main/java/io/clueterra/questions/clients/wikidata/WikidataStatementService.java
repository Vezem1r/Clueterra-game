package io.clueterra.questions.clients.wikidata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.DataSourceException;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.wikidata.dto.MetricType;
import io.clueterra.questions.clients.wikidata.dto.WikidataMetric;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class WikidataStatementService {

    private static final String END_TIME_PROPERTY = "P582";
    private static final int LABEL_BATCH_SIZE = 40;

    private final HttpClientHelper httpClient;
    private final ObjectMapper objectMapper;

    public Map<WikidataMetric, List<String>> fetchAllValues(String restBaseUrl,
                                                            String actionApiUrl,
                                                            String itemId,
                                                            Duration timeout) {
        String url = "%s/entities/items/%s/statements".formatted(restBaseUrl, itemId);
        JsonNode statementsByProperty = readJson(url, timeout);
        Map<WikidataMetric, List<String>> rawValuesByMetric = new EnumMap<>(WikidataMetric.class);
        Set<String> referencedItemIds = new LinkedHashSet<>();

        for (WikidataMetric metric : WikidataMetric.values()) {
            List<String> rawValues = selectCurrentValues(statementsByProperty.path(metric.getPropertyId()));
            rawValuesByMetric.put(metric, rawValues);
            if (metric.getType() == MetricType.ITEM_LABEL_SET) {
                referencedItemIds.addAll(rawValues);
            }
        }

        Map<String, String> labels = resolveEnglishLabels(actionApiUrl, referencedItemIds, timeout);
        Map<WikidataMetric, List<String>> resolved = new EnumMap<>(WikidataMetric.class);
        rawValuesByMetric.forEach((metric, rawValues) -> {
            List<String> values = metric.getType() == MetricType.STRING_SET
                    ? rawValues
                    : rawValues.stream().map(labels::get).filter(StringUtils::hasText).toList();
            resolved.put(metric, values);
        });
        return Map.copyOf(resolved);
    }

    private List<String> selectCurrentValues(JsonNode statements) {
        if (!statements.isArray()) {
            return List.of();
        }

        List<JsonNode> current = StreamSupport.stream(statements.spliterator(), false)
                .filter(this::isCurrentValueStatement)
                .toList();
        boolean hasPreferred = current.stream()
                .anyMatch(statement -> "preferred".equals(statement.path("rank").asText()));
        Predicate<JsonNode> selectedRank = hasPreferred
                ? statement -> "preferred".equals(statement.path("rank").asText())
                : statement -> true;

        Set<String> rawValues = new LinkedHashSet<>();
        current.stream()
                .filter(selectedRank)
                .map(statement -> statement.path("value").path("content"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .filter(StringUtils::hasText)
                .forEach(rawValues::add);

        return List.copyOf(rawValues);
    }

    private boolean isCurrentValueStatement(JsonNode statement) {
        if ("deprecated".equals(statement.path("rank").asText())) {
            return false;
        }
        if (!"value".equals(statement.path("value").path("type").asText())) {
            return false;
        }
        return StreamSupport.stream(statement.path("qualifiers").spliterator(), false)
                .noneMatch(qualifier -> END_TIME_PROPERTY.equals(qualifier.path("property").path("id").asText()));
    }

    public Map<String, String> resolveEnglishLabels(String actionApiUrl,
                                                    Set<String> itemIds,
                                                    Duration timeout) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }

        Map<String, String> labels = new LinkedHashMap<>();
        List<String> orderedIds = new ArrayList<>(itemIds);
        for (int offset = 0; offset < orderedIds.size(); offset += LABEL_BATCH_SIZE) {
            List<String> batch = orderedIds.subList(
                    offset, Math.min(offset + LABEL_BATCH_SIZE, orderedIds.size()));
            String ids = URLEncoder.encode(String.join("|", batch), StandardCharsets.UTF_8);
            String url = actionApiUrl
                    + "?action=wbgetentities&format=json&props=labels&languages=en&languagefallback=1&ids=" + ids;
            JsonNode entities = readJson(url, timeout).path("entities");
            for (String itemId : batch) {
                String label = entities.path(itemId).path("labels").path("en").path("value").asText();
                if (StringUtils.hasText(label)) {
                    labels.put(itemId, label);
                }
            }
        }
        return Map.copyOf(labels);
    }

    private JsonNode readJson(String url, Duration timeout) {
        try {
            String body = httpClient.get(DataSource.WIKIDATA, url, timeout, Map.of("Accept", "application/json"));
            return objectMapper.readTree(body);
        } catch (DataSourceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DataSourceException(
                    DataSource.WIKIDATA, "Failed to parse Wikidata response from " + url, exception);
        }
    }
}
