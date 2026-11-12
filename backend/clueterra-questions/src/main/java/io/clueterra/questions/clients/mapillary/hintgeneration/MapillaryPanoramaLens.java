package io.clueterra.questions.clients.mapillary.hintgeneration;

import io.clueterra.questions.clients.common.AbstractCachedLens;
import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfig;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MapillaryPanoramaLens extends AbstractCachedLens {

    private static final BigDecimal DEFAULT_QUALITY = BigDecimal.valueOf(0.82);

    private final DynamicDifficultyCalculator difficultyCalculator;
    private final Set<String> seenImageUrls = new HashSet<>();

    @Override
    public String name() {
        return "mapillary-panorama";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("MAPILLARY_");
    }

    @Override
    public Set<DataSource> claimedSources() {
        return Set.of(DataSource.MAPILLARY);
    }

    @Override
    public boolean canApply(StatisticContext context) {
        return context.statistic().getSource() == DataSource.MAPILLARY
                && context.statistic().getIndicatorCode() != null
                && context.statistic().getIndicatorCode().startsWith("MAPILLARY_");
    }

    @Override
    protected void loadCache() {
        seenImageUrls.clear();
    }

    @Override
    protected List<CandidateHint> doGenerate(StatisticContext context) {
        CountryStatistic stat = context.statistic();
        if (!StringUtils.hasText(stat.getImageUrl())) {
            return List.of();
        }

        if (!seenImageUrls.add(stat.getImageUrl())) {
            return List.of();
        }

        String indicatorCode = stat.getIndicatorCode();
        int variant = Math.floorMod(stat.getImageUrl().hashCode(), MapillaryPanoramaHintSupport.POOL_SIZE) + 1;
        String templateCode = MapillaryPanoramaHintSupport.templateCode(indicatorCode, variant);
        int baseDifficulty = MapillaryPanoramaHintSupport.baseDifficulty(indicatorCode);

        int difficulty = difficultyCalculator.computeDifficulty(
                templateCode,
                1,
                IndicatorConfig.getBaseDifficulty(templateCode, baseDifficulty)
        );
        BigDecimal quality = difficultyCalculator.computeQuality(
                templateCode,
                1,
                IndicatorConfig.getQualityScore(templateCode, DEFAULT_QUALITY)
        );

        return TemplatedHintBuilder.buildTemplatedCandidate(
                templateCode,
                indicatorCode,
                Map.of(),
                HintType.PANORAMA,
                Category.GEOGRAPHY,
                DataSource.MAPILLARY,
                stat.getYear(),
                difficulty,
                quality,
                1,
                name(),
                stat.getImageUrl()
        ).stream().toList();
    }
}
