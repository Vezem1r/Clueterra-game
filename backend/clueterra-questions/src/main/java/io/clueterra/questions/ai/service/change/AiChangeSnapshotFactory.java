package io.clueterra.questions.ai.service.change;

import io.clueterra.questions.ai.model.changeset.AiGeneratedHintProposal;
import io.clueterra.questions.ai.model.generation.AiGenerationImageAssessment;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.Hint;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AiChangeSnapshotFactory {

    public Map<String, Object> snapshotHint(Hint hint, Country country) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hintId", hint.getId());
        snapshot.put("countryId", hint.getCountryId());
        snapshot.put("countryCode", country != null ? country.getIsoAlpha2() : null);
        snapshot.put("countryName", country != null ? country.getNameEn() : null);
        snapshot.put("category", hint.getCategory() != null ? hint.getCategory().name() : null);
        snapshot.put("hintType", hint.getHintType() != null ? hint.getHintType().name() : null);
        snapshot.put("hintText", hint.getHintText());
        snapshot.put("imageUrl", hint.getImageUrl());
        snapshot.put("difficulty", hint.getDifficulty());
        snapshot.put("qualityScore", hint.getQualityScore());
        snapshot.put("source", hint.getSource() != null ? hint.getSource().name() : null);
        snapshot.put("sourceIndicator", hint.getSourceIndicator());
        snapshot.put("sourceYear", hint.getSourceYear());
        snapshot.put("contentRevisionHash", hint.getContentRevisionHash());
        snapshot.put("isActive", hint.getIsActive());
        snapshot.put("reviewStatus", hint.getReviewStatus() != null ? hint.getReviewStatus().name() : null);
        snapshot.put("reviewRetryAfterAt", hint.getReviewRetryAfterAt());
        return snapshot;
    }

    public Map<String, Object> snapshotGeneratedProposal(
            AiGeneratedHintProposal proposal,
            Country country,
            String finalImageUrl,
            boolean embeddingValidated,
            String embeddingModel,
            AiGenerationImageAssessment imageAssessment) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("countryId", proposal.countryId());
        snapshot.put("countryCode", country != null ? country.getIsoAlpha2() : null);
        snapshot.put("countryName", country != null ? country.getNameEn() : null);
        snapshot.put("category", proposal.category());
        snapshot.put("hintType", proposal.hintType());
        snapshot.put("hintText", proposal.hintText());
        snapshot.put("imageUrl", finalImageUrl);
        snapshot.put("difficulty", proposal.difficulty());
        snapshot.put("qualityScore", proposal.qualityScore());
        snapshot.put("source", proposal.source());
        snapshot.put("sourceIndicator", proposal.sourceIndicator());
        snapshot.put("sourceYear", proposal.sourceYear());
        snapshot.put("factSummary", proposal.factSummary());
        snapshot.put("evidenceSummary", proposal.evidenceSummary());
        snapshot.put("duplicateCheckSummary", proposal.duplicateCheckSummary());
        snapshot.put("searchTerms", proposal.searchTerms());
        snapshot.put("embeddingValidated", embeddingValidated);
        snapshot.put("embeddingModel", embeddingModel);
        if (imageAssessment != null) {
            snapshot.put("imageKind", imageAssessment.imageKind().name());
            snapshot.put("imageValidated", imageAssessment.validated());
            snapshot.put("imageValidationSummary", imageAssessment.validationSummary());
            snapshot.put("imageVisualDescription", imageAssessment.visualDescription());
            snapshot.put("imageAnswerTextVisibility", imageAssessment.answerTextVisibility());
            snapshot.put("imageAnswerSymbolVisibility", imageAssessment.answerSymbolVisibility());
            if (imageAssessment.resolvedImage() != null) {
                snapshot.put("imageSourcePageUrl", imageAssessment.resolvedImage().sourcePageUrl());
                snapshot.put("imageLicenseLabel", imageAssessment.resolvedImage().licenseLabel());
                snapshot.put("imageLicenseUrl", imageAssessment.resolvedImage().licenseUrl());
                snapshot.put("imageAttributionText", imageAssessment.resolvedImage().attributionText());
            }
        }
        snapshot.put("isActive", true);
        return snapshot;
    }

    public Map<String, Object> snapshotReviewProposal(
            Hint hint,
            Country country,
            String proposedText,
            String proposedImageUrl,
            Integer proposedDifficulty,
            Boolean proposedActive) {
        Map<String, Object> snapshot = snapshotHint(hint, country);
        if (proposedText != null) {
            snapshot.put("hintText", proposedText);
        }
        if (proposedImageUrl != null) {
            snapshot.put("imageUrl", proposedImageUrl);
        }
        if (proposedDifficulty != null) {
            snapshot.put("difficulty", proposedDifficulty);
        }
        if (proposedActive != null) {
            snapshot.put("isActive", proposedActive);
        }
        return snapshot;
    }
}
