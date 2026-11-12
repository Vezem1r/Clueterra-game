package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.HintType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class AiGenerationTaxonomyNormalizer {

    private static final Map<String, String> CATEGORY_ALIASES = Map.ofEntries(
            Map.entry("ARCHITECTURE", Category.LANDMARKS.name()),
            Map.entry("ART", Category.CULTURE.name()),
            Map.entry("CUISINE", Category.FOOD.name()),
            Map.entry("DANCE", Category.CULTURE.name()),
            Map.entry("FASHION", Category.CULTURE.name()),
            Map.entry("LANGUAGE", Category.CULTURE.name()),
            Map.entry("LITERATURE", Category.CULTURE.name()),
            Map.entry("MUSIC", Category.CULTURE.name()),
            Map.entry("MYTHOLOGY", Category.CULTURE.name()),
            Map.entry("RELIGION", Category.CULTURE.name()),
            Map.entry("SCIENCE", Category.TRIVIA.name()),
            Map.entry("SPACE", Category.INFRASTRUCTURE.name()),
            Map.entry("TECHNOLOGY", Category.INFRASTRUCTURE.name()),
            Map.entry("AVIATION", Category.INFRASTRUCTURE.name()),
            Map.entry("BOTANY", Category.NATURE.name()),
            Map.entry("GEOLOGY", Category.NATURE.name()),
            Map.entry("WILDLIFE", Category.NATURE.name()));

    private static final Map<String, String> HINT_TYPE_ALIASES = Map.ofEntries(
            Map.entry("ABSOLUTE", HintType.ABSOLUTE_FACT.name()),
            Map.entry("CULTURE", HintType.CULTURAL.name()),
            Map.entry("HISTORY", HintType.HISTORICAL.name()),
            Map.entry("IMAGE_FACTS", HintType.IMAGE_FACT.name()),
            Map.entry("RELATIVE", HintType.RELATIVE_FACT.name()),
            Map.entry("UNIQUE_FACT", HintType.UNIQUE_FEATURE.name()),
            Map.entry("UNIQUE_FILE", HintType.UNIQUE_FEATURE.name()));

    public String normalizeCategory(String value) {
        return normalize(value, CATEGORY_ALIASES);
    }

    public String normalizeHintType(String value) {
        return normalize(value, HINT_TYPE_ALIASES);
    }

    private String normalize(String value, Map<String, String> aliases) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return aliases.getOrDefault(normalized, normalized);
    }
}
