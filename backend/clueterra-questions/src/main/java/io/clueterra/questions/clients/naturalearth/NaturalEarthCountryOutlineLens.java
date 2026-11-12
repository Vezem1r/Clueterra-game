package io.clueterra.questions.clients.naturalearth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.image.ImageMetadata;
import io.clueterra.questions.service.hint.IndicatorConfig;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.CountryHintLens;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NaturalEarthCountryOutlineLens implements CountryHintLens {

    static final int DEFAULT_BASE_DIFFICULTY = 28;
    static final int MIN_DIFFICULTY = 16;
    static final int MAX_DIFFICULTY = 55;

    private static final BigDecimal REFERENCE_MODIFIER = new BigDecimal("0.65");
    private static final BigDecimal MODIFIER_SCALE = new BigDecimal("32");
    private static final BigDecimal DEFAULT_QUALITY = new BigDecimal("0.94");
    private final NaturalEarthCountryOutlineAssetService countryOutlineAssetService;

    @Override
    public String name() {
        return "natural-earth-country-outline";
    }

    @Override
    public DataSource source() {
        return DataSource.NATURAL_EARTH;
    }

    @Override
    public boolean isAvailable() {
        return countryOutlineAssetService.isAvailable();
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("NATURAL_EARTH_COUNTRY_OUTLINE_");
    }

    @Override
    public boolean canApply(Country country) {
        return country != null && countryOutlineAssetService.canProvide(country.getIsoAlpha2());
    }

    @Override
    public List<CandidateHint> generate(Country country) {
        if (country == null) {
            return List.of();
        }

        String imageObjectKey = countryOutlineAssetService
                .resolveObjectKey(country.getIsoAlpha2())
                .orElse(null);
        if (imageObjectKey == null) {
            return List.of();
        }

        String indicatorCode = "NATURAL_EARTH_COUNTRY_OUTLINE_"
                + pickVariant(country);
        int configuredBaseDifficulty = IndicatorConfig.getBaseDifficulty(
                indicatorCode,
                DEFAULT_BASE_DIFFICULTY);
        ImageMetadata metadata = countryOutlineAssetService.metadata();

        return TemplatedHintBuilder.buildTemplatedCandidate(
                        indicatorCode,
                        Map.of(),
                        HintType.IMAGE_FACT,
                        Category.GEOGRAPHY,
                        source(),
                        null,
                        calculateDifficulty(configuredBaseDifficulty, country.getDifficultyModifier()),
                        IndicatorConfig.getQualityScore(indicatorCode, DEFAULT_QUALITY),
                        1,
                        name(),
                        imageObjectKey)
                .map(candidate -> candidate.withImageMetadata(
                        metadata.originalImageUrl(),
                        metadata.sourcePageUrl(),
                        metadata.authorName(),
                        metadata.attributionText(),
                        metadata.licenseLabel(),
                        metadata.licenseUrl()))
                .map(List::of)
                .orElseGet(List::of);
    }

    static int calculateDifficulty(int baseDifficulty, BigDecimal difficultyModifier) {
        BigDecimal modifier = difficultyModifier != null ? difficultyModifier : BigDecimal.ONE;
        int adjustment = modifier.subtract(REFERENCE_MODIFIER)
                .multiply(MODIFIER_SCALE)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        return Math.clamp(baseDifficulty + adjustment, MIN_DIFFICULTY, MAX_DIFFICULTY);
    }

    private int pickVariant(Country country) {
        String countryCode = country.getIsoAlpha2() != null
                ? country.getIsoAlpha2()
                : country.getIsoAlpha3();
        return Math.floorMod((countryCode + "|" + "country-outline").hashCode(), 3) + 1;
    }
}
