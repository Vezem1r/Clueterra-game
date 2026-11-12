package io.clueterra.questions.service.hint;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.service.deduplication.DeduplicationService;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class HintFactory {

    private final HintSanitizer hintSanitizer;
    private final DeduplicationService deduplicationService;

    public Optional<Hint> fromCandidate(Country country, CandidateHint candidate) {
        if (candidate.source() == null) {
            log.warn("Skipping hint without source for {} from lens {}: {}",
                    country.getIsoAlpha2(), candidate.lensName(), candidate.text());
            return Optional.empty();
        }
        String sanitizedText = hintSanitizer.sanitize(candidate.text(), country);
        if (sanitizedText == null) {
            log.debug("Filtered hint that revealed country name for {}: {}", country.getIsoAlpha2(), candidate.text());
            return Optional.empty();
        }

        return Optional.of(Hint.builder()
                .countryId(country.getId())
                .category(candidate.category() != null ? candidate.category() : Category.GENERAL)
                .hintType(candidate.type() != null ? candidate.type() : HintType.ABSOLUTE_FACT)
                .hintText(sanitizedText)
                .imageUrl(candidate.imageUrl())
                .originalImageUrl(candidate.originalImageUrl())
                .imageSourcePageUrl(candidate.imageSourcePageUrl())
                .imageAuthorName(candidate.imageAuthorName())
                .imageAttributionText(candidate.imageAttributionText())
                .imageLicenseLabel(candidate.imageLicenseLabel())
                .imageLicenseUrl(candidate.imageLicenseUrl())
                .difficulty(candidate.estimatedDifficulty())
                .qualityScore(candidate.estimatedQuality())
                .source(candidate.source())
                .sourceIndicator(candidate.sourceIndicator())
                .sourceYear(candidate.sourceYear())
                .contentHash(deduplicationService.generateContentHash(sanitizedText))
                .contentRevisionHash(deduplicationService.generateRevisionHash(sanitizedText, candidate.imageUrl()))
                .ambiguityCount(candidate.ambiguityCount())
                .isActive(true)
                .timesUsed(0)
                .build());
    }
}
