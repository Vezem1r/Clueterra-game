package io.clueterra.questions.ai.service.support;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.ai.model.generation.AiGeneratedImageKind;

public final class AiHintSourceIndicator {

    public static final String FALLBACK = "AI_GENERATED";
    private static final String PREFIX = "AI_";

    private AiHintSourceIndicator() {
    }

    /**
     * Challenge hint allocation rejects a second candidate sharing a source_indicator, so a single
     * constant for every AI hint caps them at one slot per country regardless of pool size.
     */
    public static String forCategory(String category) {
        if (category == null || category.isBlank()) {
            return FALLBACK;
        }
        try {
            return PREFIX + Category.valueOf(category.trim()).name();
        } catch (IllegalArgumentException ex) {
            return FALLBACK;
        }
    }

    public static String forGenerated(String category, AiGeneratedImageKind imageKind) {
        if (imageKind == AiGeneratedImageKind.CURRENCY) {
            return "AI_CURRENCY";
        }
        if (imageKind == AiGeneratedImageKind.LANDMARK) {
            return "AI_LANDMARK";
        }
        return forCategory(category);
    }
}
