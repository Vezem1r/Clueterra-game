package io.clueterra.questions.clients.wikidata.cinema;

import com.fasterxml.jackson.databind.JsonNode;
import io.clueterra.questions.clients.wikidata.WikidataSparqlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WikidataCinemaQueryService {

    private static final int WORK_QUERY_BATCH_SIZE = 40;
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("Q[1-9][0-9]*");

    private static final String ALL_PEOPLE_QUERY = """
            SELECT DISTINCT ?country ?person ?occupation ?sitelinks WHERE {
              VALUES ?occupation { wd:Q10800557 wd:Q2526255 }
              ?person wdt:P106 ?occupation;
                      wdt:P19 ?birthPlace;
                      wikibase:sitelinks ?sitelinks.
              ?birthPlace wdt:P17 ?country.
              FILTER(?sitelinks >= 15)
            }
            LIMIT 30000
            """;

    private final WikidataSparqlService sparqlService;

    public List<WikidataCinemaPersonCandidate> fetchAllPeople(String queryUrl, Duration timeout) {
        return parsePeople(sparqlService.executeQuery(queryUrl, ALL_PEOPLE_QUERY, timeout));
    }

    public List<WikidataCinemaPersonCandidate> fetchPeopleForCountry(String queryUrl,
                                                                      String countryItemId,
                                                                      Duration timeout) {
        requireItemId(countryItemId);
        String query = """
                SELECT DISTINCT ?country ?person ?occupation ?sitelinks WHERE {
                  VALUES ?occupation { wd:Q10800557 wd:Q2526255 }
                  BIND(wd:%s AS ?country)
                  ?person wdt:P106 ?occupation;
                          wdt:P19 ?birthPlace;
                          wikibase:sitelinks ?sitelinks.
                  ?birthPlace wdt:P17 ?country.
                  FILTER(?sitelinks >= 15)
                }
                ORDER BY DESC(?sitelinks)
                LIMIT 150
                """.formatted(countryItemId);
        return parsePeople(sparqlService.executeQuery(queryUrl, query, timeout));
    }

    public List<WikidataCinemaWorkCandidate> fetchWorks(
            String queryUrl,
            List<WikidataCinemaPersonCandidate> candidates,
            Duration timeout) {
        List<WikidataCinemaWorkTarget> targets = candidates.stream()
                .flatMap(candidate -> candidate.factTypes().stream()
                        .map(factType -> new WikidataCinemaWorkTarget(candidate.personItemId(), factType)))
                .distinct()
                .toList();
        if (targets.isEmpty()) {
            return List.of();
        }

        List<WikidataCinemaWorkCandidate> works = new ArrayList<>();
        for (int offset = 0; offset < targets.size(); offset += WORK_QUERY_BATCH_SIZE) {
            List<WikidataCinemaWorkTarget> batch = targets.subList(
                    offset, Math.min(offset + WORK_QUERY_BATCH_SIZE, targets.size()));
            JsonNode bindings = sparqlService.executeQuery(queryUrl, buildWorksQuery(batch), timeout);
            parseWorks(bindings).forEach(works::add);
        }
        return List.copyOf(works);
    }

    private List<WikidataCinemaPersonCandidate> parsePeople(JsonNode bindings) {
        Map<String, String> countryByKey = new LinkedHashMap<>();
        Map<String, String> personByKey = new LinkedHashMap<>();
        Map<String, Integer> sitelinksByKey = new HashMap<>();
        Map<String, Set<WikidataCinemaFactType>> typesByKey = new LinkedHashMap<>();

        for (JsonNode binding : bindings) {
            String countryItemId = itemId(binding, "country");
            String personItemId = itemId(binding, "person");
            String occupationItemId = itemId(binding, "occupation");
            WikidataCinemaFactType factType = WikidataCinemaFactType.fromOccupationItemId(occupationItemId);
            if (StringUtils.hasText(countryItemId)
                    && StringUtils.hasText(personItemId)
                    && factType != null) {
                String key = candidateKey(countryItemId, personItemId);
                countryByKey.putIfAbsent(key, countryItemId);
                personByKey.putIfAbsent(key, personItemId);
                sitelinksByKey.merge(key, integerValue(binding, "sitelinks"), Math::max);
                typesByKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(factType);
            }
        }

        return typesByKey.entrySet().stream()
                .map(entry -> new WikidataCinemaPersonCandidate(
                        countryByKey.get(entry.getKey()),
                        personByKey.get(entry.getKey()),
                        entry.getValue(),
                        sitelinksByKey.getOrDefault(entry.getKey(), 0)))
                .sorted(Comparator.comparingInt(WikidataCinemaPersonCandidate::sitelinks).reversed()
                        .thenComparing(WikidataCinemaPersonCandidate::personItemId))
                .toList();
    }

    private List<WikidataCinemaWorkCandidate> parseWorks(JsonNode bindings) {
        Map<String, WikidataCinemaWorkCandidate> unique = new LinkedHashMap<>();
        for (JsonNode binding : bindings) {
            String personItemId = itemId(binding, "person");
            String workItemId = itemId(binding, "work");
            String factTypeName = binding.path("factType").path("value").asText();
            if (StringUtils.hasText(personItemId)
                    && StringUtils.hasText(workItemId)
                    && StringUtils.hasText(factTypeName)) {
                WikidataCinemaFactType factType = WikidataCinemaFactType.valueOf(factTypeName);
                WikidataCinemaWorkCandidate candidate = new WikidataCinemaWorkCandidate(
                        personItemId, factType, workItemId, integerValue(binding, "sitelinks"));
                unique.merge(
                        workKey(personItemId, factType, workItemId),
                        candidate,
                        (first, second) -> first.sitelinks() >= second.sitelinks() ? first : second);
            }
        }
        return List.copyOf(unique.values());
    }

    private String buildWorksQuery(List<WikidataCinemaWorkTarget> targets) {
        String values = targets.stream()
                .map(target -> "(wd:%s \"%s\" wdt:%s)".formatted(
                        target.personItemId(),
                        target.factType().name(),
                        target.factType().workRelationProperty()))
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();
        return """
                SELECT DISTINCT ?person ?factType ?work ?sitelinks WHERE {
                  VALUES (?person ?factType ?relation) { %s }
                  ?work ?relation ?person;
                        wdt:P31 wd:Q11424;
                        wikibase:sitelinks ?sitelinks.
                  FILTER(?sitelinks >= 8)
                }
                LIMIT 5000
                """.formatted(values);
    }

    private String itemId(JsonNode binding, String field) {
        String uri = binding.path(field).path("value").asText();
        if (!StringUtils.hasText(uri)) {
            return null;
        }
        String itemId = uri.substring(uri.lastIndexOf('/') + 1);
        return ITEM_ID_PATTERN.matcher(itemId).matches() ? itemId : null;
    }

    private int integerValue(JsonNode binding, String field) {
        return binding.path(field).path("value").asInt(0);
    }

    private void requireItemId(String itemId) {
        if (!StringUtils.hasText(itemId) || !ITEM_ID_PATTERN.matcher(itemId).matches()) {
            throw new IllegalArgumentException("Invalid Wikidata item id: " + itemId);
        }
    }

    private String candidateKey(String countryItemId, String personItemId) {
        return countryItemId + "|" + personItemId;
    }

    private String workKey(String personItemId,
                           WikidataCinemaFactType factType,
                           String workItemId) {
        return personItemId + "|" + factType.name() + "|" + workItemId;
    }
}
