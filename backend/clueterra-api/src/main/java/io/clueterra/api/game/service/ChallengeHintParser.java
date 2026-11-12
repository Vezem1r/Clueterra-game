package io.clueterra.api.game.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.game.dto.ChallengeMetadata;
import io.clueterra.api.game.dto.HintDto;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChallengeHintParser {

    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_REF = new TypeReference<>() {
    };

    public List<CountryEntry> parseCountryEntries(String countriesJson) {
        try {
            List<Map<String, Object>> data = objectMapper.readValue(countriesJson, LIST_MAP_REF);
            return data.stream()
                    .map(m -> new CountryEntry(
                            ((Number) m.get("countryId")).longValue(),
                            ((Number) m.get("order")).intValue()))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse challenge countries", e);
        }
    }

    public List<SessionCountrySeed> parseSessionCountries(String countriesJson, String hintsJson) {
        List<CountryEntry> countries = parseCountryEntries(countriesJson);
        Map<Integer, Integer> hintCounts = countHintsByCountryOrder(hintsJson);
        return countries.stream()
                .map(entry -> new SessionCountrySeed(
                        entry.countryId(),
                        entry.order(),
                        hintCounts.getOrDefault(entry.order(), 0)))
                .toList();
    }

    public HintDto getHintAtPosition(String hintsJson, int countryOrder, int hintPosition) {
        try {
            List<Map<String, Object>> data = objectMapper.readValue(hintsJson, LIST_MAP_REF);
            for (Map<String, Object> h : data) {
                int co = ((Number) h.get("countryOrder")).intValue();
                int ho = ((Number) h.get("hintOrder")).intValue();
                if (co == countryOrder && ho == hintPosition) {
                    return toHintDto(h);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse hints JSON", e);
        }
        return null;
    }

    public int countHintsPerCountry(String hintsJson) {
        try {
            return countHintsByCountryOrder(hintsJson).values().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
        } catch (Exception e) {
            log.error("Failed to count hints in JSON", e);
            return 0;
        }
    }

    public List<HintDto> getHintsForCountry(String hintsJson, int countryOrder, int revealedCount) {
        List<HintDto> hints = new ArrayList<>();
        try {
            List<Map<String, Object>> data = objectMapper.readValue(hintsJson, LIST_MAP_REF);
            for (Map<String, Object> h : data) {
                int co = ((Number) h.get("countryOrder")).intValue();
                int ho = ((Number) h.get("hintOrder")).intValue();
                if (co == countryOrder && ho <= revealedCount) {
                    hints.add(toHintDto(h));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse hints JSON", e);
        }
        return hints;
    }

    public ChallengeMetadata extractMetadata(String hintsJson) {
        try {
            List<Map<String, Object>> data = objectMapper.readValue(hintsJson, LIST_MAP_REF);
            Set<String> sources = new HashSet<>();
            Set<String> categories = new HashSet<>();
            for (Map<String, Object> hint : data) {
                sources.add((String) hint.get("source"));
                categories.add((String) hint.get("category"));
            }
            return new ChallengeMetadata(sources.size(), categories.size());
        } catch (Exception e) {
            log.error("Failed to extract hint metadata from JSON", e);
            return new ChallengeMetadata(0, 0);
        }
    }

    public Map<Integer, Integer> countHintsByCountryOrder(String hintsJson) {
        try {
            List<Map<String, Object>> data = objectMapper.readValue(hintsJson, LIST_MAP_REF);
            Map<Integer, Integer> counts = new LinkedHashMap<>();
            for (Map<String, Object> hint : data) {
                int countryOrder = ((Number) hint.get("countryOrder")).intValue();
                counts.merge(countryOrder, 1, Integer::sum);
            }
            return counts;
        } catch (Exception e) {
            log.error("Failed to count hints by country in JSON", e);
            return Map.of();
        }
    }

    private HintDto toHintDto(Map<String, Object> h) {
        Long hintId = ((Number) h.get("hintId")).longValue();
        return new HintDto(
                hintId,
                (String) h.get("text"),
                ((Number) h.get("difficulty")).intValue(),
                (String) h.get("category"),
                (String) h.get("type"),
                (String) h.get("source"),
                storageService.resolveHintAssetUrl(hintId, (String) h.get("imageUrl")),
                (String) h.get("imageSourcePageUrl"),
                (String) h.get("imageAuthorName"),
                (String) h.get("imageAttributionText"),
                (String) h.get("imageLicenseLabel"),
                (String) h.get("imageLicenseUrl"));
    }

    public record CountryEntry(long countryId, int order) {
    }

    public record SessionCountrySeed(long countryId, int order, int totalHints) {
    }
}
