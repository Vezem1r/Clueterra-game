package io.clueterra.questions.clients.manual;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.service.hint.IndicatorConfig;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.CountryHintLens;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ManualCountryLens implements CountryHintLens {

    @Override
    public String name() {
        return "manual-country";
    }

    @Override
    public DataSource source() {
        return DataSource.MANUAL;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("MANUAL_FLAG_IMAGE_");
    }

    @Override
    public boolean canApply(Country country) {
        return country != null;
    }

    @Override
    public List<CandidateHint> generate(Country country) {
        List<CandidateHint> hints = new ArrayList<>();

        String flagImageUrl = LocalFlagAssetUrlSupport.buildFlagAssetUrl(country.getIsoAlpha2())
                .orElse(null);
        if (StringUtils.hasText(flagImageUrl)) {
            addHint(hints, "MANUAL_FLAG_IMAGE_" + pickVariant(country, "flag-image", 3),
                    Map.of(),
                    Category.CULTURE,
                    HintType.IMAGE_FACT,
                    1,
                    18,
                    new BigDecimal("0.86"),
                    flagImageUrl);
            if (!hints.isEmpty()) {
                CandidateHint lastHint = hints.get(hints.size() - 1);
                hints.set(hints.size() - 1, lastHint.withOriginalImageUrl(flagImageUrl));
            }
        }

        return hints;
    }

    private void addHint(List<CandidateHint> hints,
                         String indicatorCode,
                         Map<String, ?> params,
                         Category category,
                         HintType hintType,
                         int ambiguity,
                         int defaultDifficulty,
                         BigDecimal defaultQuality,
                         String imageUrl) {
        if (!IndicatorConfig.hasTemplate(indicatorCode)) {
            return;
        }

        TemplatedHintBuilder.buildTemplatedCandidate(
                indicatorCode,
                params,
                hintType,
                category,
                DataSource.MANUAL,
                null,
                IndicatorConfig.getBaseDifficulty(indicatorCode, defaultDifficulty),
                IndicatorConfig.getQualityScore(indicatorCode, defaultQuality),
                ambiguity,
                name(),
                imageUrl
        ).ifPresent(hints::add);
    }

    private int pickVariant(Country country, String salt, int variants) {
        String seed = (country.getIsoAlpha2() != null ? country.getIsoAlpha2() : country.getIsoAlpha3()) + "|" + salt;
        return Math.floorMod(seed.hashCode(), variants) + 1;
    }
}
