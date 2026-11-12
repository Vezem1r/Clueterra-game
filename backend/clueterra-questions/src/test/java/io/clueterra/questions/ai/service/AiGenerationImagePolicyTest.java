package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.model.generation.AiGeneratedImageKind;
import io.clueterra.questions.ai.model.generation.AiGenerationImageValidationItemResult;
import io.clueterra.questions.ai.service.generation.AiGenerationImagePolicy;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.HintType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerationImagePolicyTest {

    private final AiGenerationImagePolicy policy = new AiGenerationImagePolicy();

    @Test
    void acceptsAmbiguousCurrencyTextWhenEveryOtherVisualCheckPasses() {
        AiGenerationImageValidationItemResult result = result(
                true, "CURRENCY", "AMBIGUOUS", "SUBTLE", true);

        assertThat(policy.isVisuallyApproved(AiGeneratedImageKind.CURRENCY, result)).isTrue();
    }

    @Test
    void rejectsClearAnswerTextEvenWhenModelMarksImageApproved() {
        AiGenerationImageValidationItemResult result = result(
                true, "CURRENCY", "CLEAR", "NONE", true);

        assertThat(policy.isVisuallyApproved(AiGeneratedImageKind.CURRENCY, result)).isFalse();
    }

    @Test
    void rejectsWrongVisualKindAndDescriptionMismatch() {
        assertThat(policy.isVisuallyApproved(
                AiGeneratedImageKind.LANDMARK,
                result(true, "OTHER", "NONE", "NONE", true))).isFalse();
        assertThat(policy.isVisuallyApproved(
                AiGeneratedImageKind.LANDMARK,
                result(true, "LANDMARK", "NONE", "NONE", false))).isFalse();
    }

    @Test
    void retainsStandaloneSpecializedClueAsTextWhenImageIsUnavailable() {
        assertThat(policy.shouldRetainAsText(
                AiGeneratedImageKind.CURRENCY,
                false,
                false,
                null,
                Category.ECONOMY.name(),
                HintType.IMAGE_FACT.name(),
                false)).isTrue();
    }

    @Test
    void doesNotRetainClueThatRequiresTheMissingImage() {
        assertThat(policy.shouldRetainAsText(
                AiGeneratedImageKind.LANDMARK,
                true,
                false,
                null,
                Category.LANDMARKS.name(),
                HintType.IMAGE_FACT.name(),
                true)).isFalse();
    }

    @Test
    void retainsStandaloneClueWhenValidatedImageKindIsIncompatible() {
        assertThat(policy.shouldRetainAsText(
                AiGeneratedImageKind.CURRENCY,
                true,
                true,
                AiGeneratedImageKind.LANDMARK,
                Category.ECONOMY.name(),
                HintType.IMAGE_FACT.name(),
                false)).isTrue();
    }

    private AiGenerationImageValidationItemResult result(
            boolean approved,
            String detectedKind,
            String textVisibility,
            String symbolVisibility,
            boolean descriptionMatches) {
        return new AiGenerationImageValidationItemResult(
                0,
                approved,
                detectedKind,
                textVisibility,
                symbolVisibility,
                descriptionMatches,
                "visible subject",
                "assessment");
    }
}
