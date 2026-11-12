package io.clueterra.questions.clients.curatedfacts;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.clueterra.questions.clients.common.CountryReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class CuratedCountryFactsLoader {

    private static final String DATA_DIRECTORY = "data/curated-country-facts/";

    private final ObjectMapper yaml = new ObjectMapper(YAMLFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());

    public Map<String, String> load(String fileName) {
        String path = DATA_DIRECTORY + fileName;
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            Map<?, ?> raw = yaml.readValue(input, Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> addEntry(result, path, key, value));
            log.info("[CuratedCountryFacts] Loaded {} entries from {}", result.size(), fileName);
            return Map.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load curated country facts from " + path, exception);
        }
    }

    private void addEntry(Map<String, String> target, String path, Object rawKey, Object rawValue) {
        if (rawKey == null || rawValue == null) {
            throw new IllegalStateException("Null country fact entry in " + path);
        }

        String countryCode = rawKey.toString().trim().toUpperCase(Locale.ROOT);
        String value = rawValue.toString().trim();
        if (countryCode.length() != 2 || CountryReference.getCountryName(countryCode) == null) {
            throw new IllegalStateException("Unknown ISO alpha-2 code " + countryCode + " in " + path);
        }
        if (value.isBlank()) {
            throw new IllegalStateException("Blank value for " + countryCode + " in " + path);
        }
        target.put(countryCode, value);
    }
}

