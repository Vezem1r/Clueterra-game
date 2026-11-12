package io.clueterra.questions.service.hint;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IndicatorTemplateRendererTest {

    private final IndicatorTemplateRenderer renderer = new IndicatorTemplateRenderer();

    @Test
    void renderTemplate_replacesBraceStylePlaceholdersAndTreatsNullAsEmptyString() {
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("name", "Alfred Nobel");
        placeholders.put("year", 1901);
        placeholders.put("optional", null);

        String rendered = renderer.renderTemplate(
                "{name} won the prize in {year}. Optional: '{optional}'",
                placeholders
        );

        assertThat(rendered).isEqualTo("Alfred Nobel won the prize in 1901. Optional: ''");
    }
}
