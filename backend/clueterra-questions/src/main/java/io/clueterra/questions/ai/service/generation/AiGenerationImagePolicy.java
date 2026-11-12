package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.ai.model.generation.AiGeneratedImageKind;
import io.clueterra.questions.ai.model.generation.AiGenerationImageValidationItemResult;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.HintType;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AiGenerationImagePolicy {

    private static final String CLEAR = "CLEAR";

    public boolean isVisuallyApproved(
            AiGeneratedImageKind expectedKind,
            AiGenerationImageValidationItemResult result) {
        if (result == null) {
            return false;
        }
        return Boolean.TRUE.equals(result.approved())
                && expectedKind.name().equals(normalize(result.detectedKind()))
                && !CLEAR.equals(normalize(result.answerTextVisibility()))
                && !CLEAR.equals(normalize(result.answerSymbolVisibility()))
                && Boolean.TRUE.equals(result.descriptionMatchesImage());
    }

    public boolean shouldRetainAsText(
            AiGeneratedImageKind declaredKind,
            boolean proposedImage,
            boolean imageValidated,
            AiGeneratedImageKind validatedKind,
            String category,
            String hintType,
            boolean textReferencesImage) {
        boolean incompatibleValidatedImage = imageValidated
                && !isKindCompatible(validatedKind, category, hintType);
        boolean imageUnavailable = (isSpecializedKind(declaredKind) && !proposedImage)
                || (proposedImage && !imageValidated)
                || incompatibleValidatedImage
                || (!proposedImage && HintType.IMAGE_FACT.name().equals(hintType));
        return imageUnavailable && !textReferencesImage;
    }

    public boolean isKindCompatible(
            AiGeneratedImageKind imageKind,
            String category,
            String hintType) {
        if (imageKind == null || !HintType.IMAGE_FACT.name().equals(hintType)) {
            return false;
        }
        return switch (imageKind) {
            case CURRENCY -> Category.ECONOMY.name().equals(category);
            case LANDMARK -> Category.LANDMARKS.name().equals(category);
            case OTHER -> true;
        };
    }

    public String normalize(String value) {
        return value != null && !value.isBlank()
                ? value.trim().toUpperCase(Locale.ROOT)
                : null;
    }

    private boolean isSpecializedKind(AiGeneratedImageKind imageKind) {
        return imageKind == AiGeneratedImageKind.LANDMARK || imageKind == AiGeneratedImageKind.CURRENCY;
    }
}
