package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.service.generation.AiGenerationTaxonomyNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerationTaxonomyNormalizerTest {

    private final AiGenerationTaxonomyNormalizer normalizer = new AiGenerationTaxonomyNormalizer();

    @Test
    void preservesBackendEnumNamesAndNormalizesCase() {
        assertThat(normalizer.normalizeCategory(" history ")).isEqualTo("HISTORY");
        assertThat(normalizer.normalizeHintType("unique_feature")).isEqualTo("UNIQUE_FEATURE");
    }

    @Test
    void mapsUsefulKnowledgeDomainsToBackendCategories() {
        assertThat(normalizer.normalizeCategory("MUSIC")).isEqualTo("CULTURE");
        assertThat(normalizer.normalizeCategory("literature")).isEqualTo("CULTURE");
        assertThat(normalizer.normalizeCategory("ARCHITECTURE")).isEqualTo("LANDMARKS");
        assertThat(normalizer.normalizeCategory("SCIENCE")).isEqualTo("TRIVIA");
    }

    @Test
    void repairsCommonHintTypeAliases() {
        assertThat(normalizer.normalizeHintType("UNIQUE_FILE")).isEqualTo("UNIQUE_FEATURE");
        assertThat(normalizer.normalizeHintType("image_facts")).isEqualTo("IMAGE_FACT");
    }
}
