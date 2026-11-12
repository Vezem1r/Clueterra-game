package io.clueterra.questions.clients.citybikes.hintgeneration;

import io.clueterra.questions.clients.common.AbstractCachedLens;
import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class CityBikesLens extends AbstractCachedLens {

    private static final BigDecimal DEFAULT_QUALITY = BigDecimal.valueOf(0.80);
    private static final Map<String, Integer> BASE_DIFFICULTIES = Map.of(
            "CITYBIKES_NETWORKS", 20,
            "CITYBIKES_CITIES", 22,
            "CITYBIKES_CITY_CLUSTER", 20,
            "CITYBIKES_TOP_NETWORK", 26,
            "CITYBIKES_EBIKES", 26,
            "CITYBIKES_OPERATOR", 24
    );

    private final StatisticRepository statisticRepository;
    private final DynamicDifficultyCalculator difficultyCalculator;
    private final AtomicReference<Map<Long, List<CountryStatistic>>> cachedByCountry = new AtomicReference<>(Map.of());

    @Override
    public String name() {
        return "citybikes";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("CITYBIKES_");
    }

    @Override
    public Set<DataSource> claimedSources() {
        return Set.of(DataSource.CITYBIKES);
    }

    @Override
    public boolean canApply(StatisticContext context) {
        return context.statistic().getSource() == DataSource.CITYBIKES
                && context.statistic().getIndicatorCode() != null
                && context.statistic().getIndicatorCode().startsWith("CITYBIKES_");
    }

    @Override
    protected void loadCache() {
        List<CountryStatistic> all = statisticRepository.findCurrentBySource(DataSource.CITYBIKES);
        Map<Long, List<CountryStatistic>> byCountry = new HashMap<>();
        for (CountryStatistic stat : all) {
            byCountry.computeIfAbsent(stat.getCountryId(), k -> new ArrayList<>()).add(stat);
        }
        cachedByCountry.set(byCountry);
        log.info("[CityBikes] Cache refreshed: {} countries", byCountry.size());
    }

    @Override
    protected List<CandidateHint> doGenerate(StatisticContext context) {
        CountryStatistic stat = context.statistic();
        if (stat.getValue() == null && !StringUtils.hasText(stat.getValueText())) {
            return List.of();
        }

        if (isLowValueIndicator(stat)) {
            return List.of();
        }

        String indicatorCode = stat.getIndicatorCode();
        String templateCode = indicatorCode + "_HINT_" + pickVariant(context, indicatorCode);
        int ambiguity = computeAmbiguity(stat);

        return TemplatedHintBuilder.buildDynamicTemplatedCandidate(
                difficultyCalculator,
                templateCode,
                indicatorCode,
                Map.of(
                        "value", stat.getValue() != null ? formatValue(stat.getValue()) : "",
                        "subject", StringUtils.hasText(stat.getValueText()) ? stat.getValueText() : "",
                        "year", stat.getYear() != null ? String.valueOf(stat.getYear()) : ""
                ),
                hintType(indicatorCode),
                stat.getCategory() != null ? stat.getCategory() : Category.INFRASTRUCTURE,
                DataSource.CITYBIKES,
                stat.getYear(),
                ambiguity,
                BASE_DIFFICULTIES.getOrDefault(indicatorCode, 24),
                DEFAULT_QUALITY,
                name()
        ).map(List::of).orElseGet(List::of);
    }

    private int computeAmbiguity(CountryStatistic stat) {
        return (int) Math.max(1, cachedByCountry.get().values().stream()
                .filter(stats -> stats.stream().anyMatch(candidate -> sameClue(stat, candidate)))
                .count());
    }

    private boolean sameClue(CountryStatistic left, CountryStatistic right) {
        if (right == null || !left.getIndicatorCode().equals(right.getIndicatorCode())) {
            return false;
        }
        if (left.getValue() != null && right.getValue() != null) {
            return left.getValue().setScale(0, RoundingMode.HALF_UP)
                    .compareTo(right.getValue().setScale(0, RoundingMode.HALF_UP)) == 0;
        }
        String leftText = normalize(left.getValueText());
        String rightText = normalize(right.getValueText());
        return StringUtils.hasText(leftText) && leftText.equals(rightText);
    }

    private static final Set<String> PLURAL_INDICATORS = Set.of("CITYBIKES_NETWORKS", "CITYBIKES_CITIES");

    private boolean isLowValueIndicator(CountryStatistic stat) {
        return PLURAL_INDICATORS.contains(stat.getIndicatorCode())
                && stat.getValue() != null
                && stat.getValue().intValue() <= 1;
    }

    private HintType hintType(String indicatorCode) {
        return switch (indicatorCode) {
            case "CITYBIKES_CITY_CLUSTER", "CITYBIKES_OPERATOR" -> HintType.CULTURAL;
            case "CITYBIKES_EBIKES" -> HintType.UNIQUE_FEATURE;
            default -> HintType.ABSOLUTE_FACT;
        };
    }

    private int pickVariant(StatisticContext ctx, String salt) {
        String seed = (ctx.countryCode() != null ? ctx.countryCode() : "XX") + "|" + salt;
        return Math.floorMod(seed.hashCode(), 3) + 1;
    }

    private String formatValue(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return "%,d".formatted(value.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
