package io.clueterra.questions.clients.nagerdate.hintgeneration;

import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.clients.common.ExpiringLensCache;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfig;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NagerDateLens implements HintLens {

    private static final String TRIGGER = "HOLIDAY_COUNT";
    private static final int GENEROUS_THRESHOLD = 15;
    private static final int STRICT_THRESHOLD = 8;
    private static final int COUNT_AMBIGUITY_THRESHOLD = 5;
    private static final int NOTABLE_AMBIGUITY_THRESHOLD = 8;
    private static final BigDecimal QUALITY_COUNT = BigDecimal.valueOf(0.75);
    private static final BigDecimal QUALITY_NOTABLE = BigDecimal.valueOf(0.80);
    private static final BigDecimal QUALITY_UNIQUE = BigDecimal.valueOf(0.92);

    private final StatisticRepository statisticRepository;
    private final DynamicDifficultyCalculator difficultyCalculator;

    private final ExpiringLensCache<Map<Long, List<CountryStatistic>>> cache =
            new ExpiringLensCache<>(Duration.ofMinutes(10));

    @Override
    public String name() {
        return "nagerdate";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("HOLIDAY_");
    }

    @Override
    public boolean canApply(StatisticContext context) {
        return TRIGGER.equals(context.statistic().getIndicatorCode());
    }

    @Override
    public void invalidateCache() {
        cache.invalidate();
    }

    @Override
    public List<CandidateHint> generate(StatisticContext context) {
        Map<Long, List<CountryStatistic>> byCountry = cache.get(this::loadByCountry);
        List<CandidateHint> hints = new ArrayList<>();
        Long countryId = context.statistic().getCountryId();
        String countryCode = context.countryCode();
        List<CountryStatistic> stats = byCountry.getOrDefault(countryId, List.of());

        int count = intValue(stats);
        if (count > 0) {
            int ambiguity = NagerDateAmbiguityCalculator.forCount(countryId, byCountry, count);
            if (ambiguity <= COUNT_AMBIGUITY_THRESHOLD) {
                String base;
                if (count >= GENEROUS_THRESHOLD) {
                    base = "HOLIDAY_GENEROUS";
                } else if (count <= STRICT_THRESHOLD) {
                    base = "HOLIDAY_STRICT";
                } else {
                    base = "HOLIDAY_STANDARD";
                }
                addHint(hints, context, HintType.ABSOLUTE_FACT, pickVariant(base, countryCode),
                        Map.of("count", String.valueOf(count)), ambiguity, QUALITY_COUNT, 35);
            }
        }

        String notableList = textValue(stats, "HOLIDAY_NOTABLE");
        if (notableList != null && !notableList.isBlank()) {
            List<String> notableCandidates = Arrays.stream(notableList.split(","))
                    .map(String::trim)
                    .filter(h -> !h.isBlank())
                    .filter(h -> NagerDateAmbiguityCalculator.forNotable(countryId, byCountry, h) <= NOTABLE_AMBIGUITY_THRESHOLD)
                    .toList();
            if (!notableCandidates.isEmpty()) {
                String holiday = notableCandidates.get(Math.abs(countryCode.hashCode()) % notableCandidates.size());
                int ambiguity = NagerDateAmbiguityCalculator.forNotable(countryId, byCountry, holiday);
                addHint(hints, context, HintType.CULTURAL, pickVariant("HOLIDAY_NOTABLE", countryCode),
                        Map.of("holiday", holiday), ambiguity, QUALITY_NOTABLE, 28);
            }
        }

        String uniqueEnList = textValue(stats, "HOLIDAY_UNIQUE_EN");
        if (uniqueEnList != null && !uniqueEnList.isBlank()) {
            String countryNameLower = context.countryName().toLowerCase();
            String nativeNameLower = context.country().getNameNative() != null
                    ? context.country().getNameNative().toLowerCase() : "";
            List<String> candidates = Arrays.stream(uniqueEnList.split(","))
                    .map(String::trim)
                    .filter(n -> !n.isBlank())
                    .filter(n -> !containsCountryName(n.toLowerCase(), countryNameLower, nativeNameLower))
                    .filter(n -> NagerDateAmbiguityCalculator.forUniqueEn(countryId, byCountry, n) <= 1)
                    .toList();
            if (!candidates.isEmpty()) {
                String chosen = candidates.get(Math.abs(countryCode.hashCode()) % candidates.size());
                int ambiguity = NagerDateAmbiguityCalculator.forUniqueEn(countryId, byCountry, chosen);
                addHint(hints, context, HintType.CULTURAL, pickVariant("HOLIDAY_UNIQUE", countryCode),
                        Map.of("name", chosen), ambiguity, QUALITY_UNIQUE, 22);
            }
        }

        return hints;
    }

    private Map<Long, List<CountryStatistic>> loadByCountry() {
        List<CountryStatistic> all = statisticRepository.findBySource(DataSource.NAGER_DATE);
        Map<Long, List<CountryStatistic>> byCountry = new HashMap<>();
        for (CountryStatistic stat : all) {
            byCountry.computeIfAbsent(stat.getCountryId(), k -> new ArrayList<>()).add(stat);
        }
        return byCountry;
    }

    private void addHint(List<CandidateHint> hints, StatisticContext ctx, HintType hintType, String code,
                         Map<String, String> params, int ambiguity, BigDecimal baseQuality, int baseDifficulty) {
        if (!IndicatorConfig.hasTemplate(code)) {
            log.warn("[NagerDate] No template for indicator: {}", code);
            return;
        }
        if (code.startsWith("HOLIDAY_UNIQUE_") && params.containsKey("name")) {
            String place = ctx.subregion() != null && !ctx.subregion().isBlank()
                    ? "In this " + ctx.subregion() + " country, " : "Here, ";
            String text = place + "\"" + params.get("name") + "\" is observed as a public holiday.";
            hints.add(TemplatedHintBuilder.buildCandidate(
                    text, hintType, Category.CULTURE, DataSource.NAGER_DATE, code,
                    ctx.statistic().getYear(),
                    difficultyCalculator.computeDifficulty(code, ambiguity, baseDifficulty),
                    difficultyCalculator.computeQuality(code, ambiguity, baseQuality),
                    ambiguity, name(), null));
            return;
        }
        TemplatedHintBuilder.buildDynamicTemplatedCandidate(
                difficultyCalculator,
                code,
                params,
                hintType,
                Category.CULTURE,
                DataSource.NAGER_DATE,
                ctx.statistic().getYear(),
                ambiguity,
                baseDifficulty,
                baseQuality,
                name()
        ).ifPresent(hints::add);
    }

    private static String pickVariant(String base, String countryCode) {
        int variants = base.startsWith("HOLIDAY_STRICT") ? 2 : 3;
        return base + "_" + (Math.abs(countryCode.hashCode()) % variants + 1);
    }
    private static int intValue(List<CountryStatistic> stats) {
        return stats.stream()
                .filter(s -> "HOLIDAY_COUNT".equals(s.getIndicatorCode()) && s.getValue() != null)
                .mapToInt(s -> s.getValue().intValue())
                .findFirst()
                .orElse(0);
    }

    private static String textValue(List<CountryStatistic> stats, String code) {
        return stats.stream()
                .filter(s -> code.equals(s.getIndicatorCode()))
                .map(CountryStatistic::getValueText)
                .findFirst()
                .orElse(null);
    }

    private static boolean containsCountryName(String nameLower, String countryNameLower, String nativeNameLower) {
        return nameLower.contains(countryNameLower)
                || (!nativeNameLower.isBlank() && nameLower.contains(nativeNameLower));
    }
}
