package io.clueterra.questions.clients.hydrorivers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.image.ImageMetadata;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.CountryHintLens;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HydroRiverSkeletonLens implements CountryHintLens {

    static final int MIN_DIFFICULTY = 12;
    static final int MAX_DIFFICULTY = 92;

    private static final int AMBIGUITY_COUNT = 1;
    private static final BigDecimal REFERENCE_MODIFIER = new BigDecimal("0.65");
    private static final BigDecimal MODIFIER_SCALE = new BigDecimal("32");
    private static final BigDecimal DEFAULT_QUALITY = new BigDecimal("0.95");
    private final HydroRiverSkeletonAssetService riverSkeletonAssetService;
    private final DynamicDifficultyCalculator dynamicDifficultyCalculator;

    @Override
    public String name() {
        return "hydrorivers-river-skeleton";
    }

    @Override
    public DataSource source() {
        return DataSource.HYDRO_RIVERS;
    }

    @Override
    public boolean isAvailable() {
        return riverSkeletonAssetService.isAvailable();
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of(HydroRiverSkeletonVariant.indicatorPrefix());
    }

    @Override
    public boolean canApply(Country country) {
        return country != null && riverSkeletonAssetService.canProvide(country.getIsoAlpha2());
    }

    @Override
    public boolean shouldCleanup(Country country) {
        // A country may have lost its bundled asset after a quality gate change.
        // Regeneration must still archive its previously generated river hints.
        return country != null && riverSkeletonAssetService.isAvailable();
    }

    @Override
    public List<CandidateHint> generate(Country country) {
        if (country == null) {
            return List.of();
        }

        List<CandidateHint> candidates = new ArrayList<>();
        for (HydroRiverSkeletonVariant variant : riverSkeletonAssetService.availableVariants(country.getIsoAlpha2())) {
            riverSkeletonAssetService.resolveObjectKey(country.getIsoAlpha2(), variant)
                    .flatMap(imageObjectKey -> buildCandidate(country, variant, imageObjectKey))
                    .ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    private Optional<CandidateHint> buildCandidate(
            Country country,
            HydroRiverSkeletonVariant variant,
            String imageObjectKey) {
        String indicatorCode = variant.indicatorCode();
        int dynamicDifficulty = dynamicDifficultyCalculator.computeDifficulty(
                indicatorCode,
                AMBIGUITY_COUNT,
                variant.defaultBaseDifficulty());
        int adjustedDifficulty = adjustForCountry(dynamicDifficulty, country.getDifficultyModifier());
        BigDecimal quality = dynamicDifficultyCalculator.computeQuality(
                indicatorCode,
                AMBIGUITY_COUNT,
                DEFAULT_QUALITY);
        ImageMetadata metadata = riverSkeletonAssetService.metadata();

        return TemplatedHintBuilder.buildTemplatedCandidate(
                        indicatorCode,
                        Map.of(),
                        HintType.IMAGE_FACT,
                        Category.GEOGRAPHY,
                        source(),
                        null,
                        adjustedDifficulty,
                        quality,
                        AMBIGUITY_COUNT,
                        name(),
                        imageObjectKey)
                .map(candidate -> candidate.withImageMetadata(
                        metadata.originalImageUrl(),
                        metadata.sourcePageUrl(),
                        metadata.authorName(),
                        metadata.attributionText(),
                        metadata.licenseLabel(),
                        metadata.licenseUrl()));
    }

    static int adjustForCountry(int baseDifficulty, BigDecimal difficultyModifier) {
        BigDecimal modifier = difficultyModifier != null ? difficultyModifier : BigDecimal.ONE;
        int adjustment = modifier.subtract(REFERENCE_MODIFIER)
                .multiply(MODIFIER_SCALE)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        return Math.clamp(baseDifficulty + adjustment, MIN_DIFFICULTY, MAX_DIFFICULTY);
    }
}
