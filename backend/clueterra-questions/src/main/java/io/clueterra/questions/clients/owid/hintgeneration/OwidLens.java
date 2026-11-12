package io.clueterra.questions.clients.owid.hintgeneration;

import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.clients.common.ExpiringLensCache;
import io.clueterra.questions.clients.owid.dto.OwidCachedData;
import io.clueterra.questions.clients.owid.dto.OwidDataset;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OwidLens implements HintLens {

    private static final String LENS = "owid";

    private static final Set<String> ALL_INDICATORS = Arrays.stream(OwidDataset.values())
            .map(OwidDataset::indicatorCode)
            .collect(Collectors.toUnmodifiableSet());

    private final StatisticRepository statisticRepository;
    private final DynamicDifficultyCalculator diffCalc;

    private final ExpiringLensCache<OwidCachedData> cache = new ExpiringLensCache<>(Duration.ofMinutes(15));

    @Override
    public String name() {
        return LENS;
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("OWID_");
    }

    @Override
    public boolean canApply(StatisticContext context) {
        return ALL_INDICATORS.contains(context.statistic().getIndicatorCode());
    }

    @Override
    public void invalidateCache() {
        cache.invalidate();
    }

    @Override
    public List<CandidateHint> generate(StatisticContext context) {
        OwidCachedData data = cache.get(this::loadData);
        if (data.totalCountries() == 0) return List.of();

        Long countryId = context.statistic().getCountryId();
        Map<String, CountryStatistic> stats = data.byCountry().getOrDefault(countryId, Map.of());
        String indicator = context.statistic().getIndicatorCode();

        List<CandidateHint> hints = new ArrayList<>();
        switch (indicator) {
            case "OWID_HAPPINESS" -> generateHappiness(hints, context, stats, data);
            case "OWID_HDI" -> generateHdi(hints, context, stats, data);
            case "OWID_DEMOCRACY" -> generateDemocracy(hints, context, stats, data);
            case "OWID_SOLAR_PCT" -> generateSolar(hints, context, stats, data);
            case "OWID_WIND_PCT" -> generateWind(hints, context, stats, data);
            case "OWID_RENEWABLES_PCT" -> generateRenewables(hints, context, stats, data);
            case "OWID_POVERTY_SHARE" -> generatePoverty(hints, context, stats, data);
            case "OWID_CO2_PER_CAPITA" -> generateCo2(hints, context, stats, data);
            case "OWID_LIFE_EXPECTANCY" -> generateLifeExpectancy(hints, context, stats, data);
            case "OWID_FERTILITY_RATE" -> generateFertility(hints, context, stats, data);
            case "OWID_INTERNET_USERS" -> generateInternet(hints, context, stats, data);
            case "OWID_ALCOHOL_CONSUMPTION" -> generateAlcohol(hints, context, stats, data);
            default -> {
                //ignore
            }
        }


        generateCombos(hints, context, stats, data);
        return hints;
    }

    private void generateHappiness(List<CandidateHint> hints, StatisticContext ctx,
                                   Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal score = val(stats, "OWID_HAPPINESS");
        if (score == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_HAPPINESS", List.of());
        int rank = rank(data, "OWID_HAPPINESS", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byBracket(score, sorted, 0.5);
        String s = fmt1(score);
        int year = year(ctx);

        if (rank <= 15) {
            hint(hints, ctx, "OWID_HAPPINESS_TOP",
                    map("rank", ordinal(rank), "score", s, "year", String.valueOf(year)),
                    HintType.RANKING, Category.DEMOGRAPHICS, amb, 68);
        } else if (rank > total - 20) {
            hint(hints, ctx, "OWID_HAPPINESS_LOW",
                    map("rank", String.valueOf(rank), "total", String.valueOf(total), "score", s),
                    HintType.RANKING, Category.DEMOGRAPHICS, amb, 70);
        } else {
            hint(hints, ctx, "OWID_HAPPINESS_MID",
                    map("rank", String.valueOf(rank), "total", String.valueOf(total), "score", s),
                    HintType.ABSOLUTE_FACT, Category.DEMOGRAPHICS, amb, 65);
        }
        hint(hints, ctx, "OWID_HAPPINESS_SCORE",
                map("score", s), HintType.ABSOLUTE_FACT, Category.DEMOGRAPHICS,
                OwidAmbiguityCalculator.byRoundedValue(score, sorted, 1), 62);

        BigDecimal avg = data.globalAvg().get("OWID_HAPPINESS");
        if (avg != null) {
            String diff = absDiff1(score, avg);
            String dir = score.compareTo(avg) >= 0 ? "above" : "below";
            hint(hints, ctx, "OWID_HAPPINESS_VS_AVG",
                    map("score", s, "diff", diff, "direction", dir, "avg", fmt1(avg)),
                    HintType.RELATIVE_FACT, Category.DEMOGRAPHICS, amb + 1, 64);
        }
        addPercentileHint(hints, ctx, rank, total, "OWID_HAPPINESS", Category.DEMOGRAPHICS, amb, false);
    }

    private void generateHdi(List<CandidateHint> hints, StatisticContext ctx,
                             Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal hdi = val(stats, "OWID_HDI");
        if (hdi == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_HDI", List.of());
        int rank = rank(data, "OWID_HDI", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byRoundedValue(hdi, sorted, 2);
        String hdiStr = hdi.setScale(3, RoundingMode.HALF_UP).toPlainString();
        double hdiD = hdi.doubleValue();
        String tier;
        if (hdiD >= 0.800) tier = "very high";
        else if (hdiD >= 0.700) tier = "high";
        else if (hdiD >= 0.550) tier = "medium";
        else tier = "low";

        if (rank <= 20) {
            hint(hints, ctx, "OWID_HDI_TOP",
                    map("value", hdiStr, "rank", ordinal(rank), "total", String.valueOf(total)),
                    HintType.RANKING, Category.DEMOGRAPHICS, amb, 65);
        } else if (rank > total - 20) {
            hint(hints, ctx, "OWID_HDI_LOW",
                    map("value", hdiStr, "rank", String.valueOf(rank), "total", String.valueOf(total)),
                    HintType.RANKING, Category.DEMOGRAPHICS, amb, 72);
        } else {
            hint(hints, ctx, "OWID_HDI_TIER",
                    map("value", hdiStr, "tier", tier, "rank", String.valueOf(rank)),
                    HintType.ABSOLUTE_FACT, Category.DEMOGRAPHICS, amb, 63);
        }
        hint(hints, ctx, "OWID_HDI_RANK",
                map("rank", String.valueOf(rank), "total", String.valueOf(total), "value", hdiStr),
                HintType.RANKING, Category.DEMOGRAPHICS, OwidAmbiguityCalculator.byRank(sorted), 67);
        addPercentileHint(hints, ctx, rank, total, "OWID_HDI", Category.DEMOGRAPHICS, amb, false);
    }

    private void generateDemocracy(List<CandidateHint> hints, StatisticContext ctx,
                                   Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal score = val(stats, "OWID_DEMOCRACY");
        if (score == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_DEMOCRACY", List.of());
        int rank = rank(data, "OWID_DEMOCRACY", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byRoundedValue(score, sorted, 2);
        String s = score.setScale(2, RoundingMode.HALF_UP).toPlainString();
        double scoreD = score.doubleValue();
        String tier;
        if (scoreD >= 0.70) tier = "most democratic";
        else if (scoreD >= 0.40) tier = "partially democratic";
        else if (scoreD >= 0.20) tier = "more authoritarian";
        else tier = "highly authoritarian";

        if (rank <= 20) {
            hint(hints, ctx, "OWID_DEMOCRACY_HIGH",
                    map("score", s, "rank", ordinal(rank)),
                    HintType.ABSOLUTE_FACT, Category.POLITICS, amb, 72);
        } else if (rank > total - 20) {
            hint(hints, ctx, "OWID_DEMOCRACY_LOW",
                    map("score", s, "tier", tier),
                    HintType.ABSOLUTE_FACT, Category.POLITICS, amb, 74);
        } else {
            hint(hints, ctx, "OWID_DEMOCRACY_MID",
                    map("score", s, "tier", tier),
                    HintType.ABSOLUTE_FACT, Category.POLITICS, amb, 68);
        }
        hint(hints, ctx, "OWID_DEMOCRACY_RANK",
                map("rank", String.valueOf(rank), "total", String.valueOf(total), "score", s),
                HintType.RANKING, Category.POLITICS, OwidAmbiguityCalculator.byRank(sorted), 70);
        addPercentileHint(hints, ctx, rank, total, "OWID_DEMOCRACY", Category.POLITICS, amb, false);
    }

    private void generateSolar(List<CandidateHint> hints, StatisticContext ctx,
                               Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal pct = val(stats, "OWID_SOLAR_PCT");
        if (pct == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_SOLAR_PCT", List.of());
        int rank = rank(data, "OWID_SOLAR_PCT", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byBracket(pct, sorted, 3.0);
        String pctStr = fmt1(pct);
        double pctD = pct.doubleValue();

        if (pctD >= 10.0) {
            hint(hints, ctx, "OWID_SOLAR_HIGH",
                    map("pct", pctStr, "rank", ordinal(rank)),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, Math.max(0, amb - 1), 74);
        } else if (pctD >= 2.0) {
            hint(hints, ctx, "OWID_SOLAR_MID",
                    map("pct", pctStr),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, amb, 68);
        } else {
            hint(hints, ctx, "OWID_SOLAR_LOW",
                    map("pct", pctStr),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, amb + 2, 65);
        }
        addPercentileHint(hints, ctx, rank, total, "OWID_SOLAR_PCT", Category.ENVIRONMENT, amb, false);
    }

    private void generateWind(List<CandidateHint> hints, StatisticContext ctx,
                              Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal pct = val(stats, "OWID_WIND_PCT");
        if (pct == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_WIND_PCT", List.of());
        int rank = rank(data, "OWID_WIND_PCT", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byBracket(pct, sorted, 4.0);
        String pctStr = fmt1(pct);
        double pctD = pct.doubleValue();

        if (pctD >= 20.0) {
            hint(hints, ctx, "OWID_WIND_HIGH",
                    map("pct", pctStr, "rank", ordinal(rank)),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, Math.max(0, amb - 1), 76);
        } else if (pctD >= 5.0) {
            hint(hints, ctx, "OWID_WIND_MID",
                    map("pct", pctStr),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, amb, 70);
        } else {
            hint(hints, ctx, "OWID_WIND_LOW",
                    map("pct", pctStr),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, amb + 2, 66);
        }
        addPercentileHint(hints, ctx, rank, total, "OWID_WIND_PCT", Category.ENVIRONMENT, amb, false);
    }

    private void generateRenewables(List<CandidateHint> hints, StatisticContext ctx,
                                    Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal pct = val(stats, "OWID_RENEWABLES_PCT");
        if (pct == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_RENEWABLES_PCT", List.of());
        int rank = rank(data, "OWID_RENEWABLES_PCT", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byBracket(pct, sorted, 5.0);
        String pctStr = fmt1(pct);
        double pctD = pct.doubleValue();

        if (pctD >= 60.0) {
            hint(hints, ctx, "OWID_RENEWABLES_HIGH",
                    map("pct", pctStr, "rank", ordinal(rank)),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, Math.max(0, amb - 1), 74);
        } else if (pctD <= 10.0) {
            hint(hints, ctx, "OWID_RENEWABLES_LOW",
                    map("pct", pctStr),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, amb, 72);
        } else {
            BigDecimal avg = data.globalAvg().get("OWID_RENEWABLES_PCT");
            String dir = avg != null && pct.compareTo(avg) >= 0 ? "above" : "below";
            hint(hints, ctx, "OWID_RENEWABLES_MID",
                    map("pct", pctStr, "direction", dir),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, amb, 66);
        }
        addPercentileHint(hints, ctx, rank, total, "OWID_RENEWABLES_PCT", Category.ENVIRONMENT, amb, false);
    }

    private void generatePoverty(List<CandidateHint> hints, StatisticContext ctx,
                                 Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal pct = val(stats, "OWID_POVERTY_SHARE");
        if (pct == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_POVERTY_SHARE", List.of());
        int rank = rank(data, "OWID_POVERTY_SHARE", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byBracket(pct, sorted, 5.0);
        String pctStr = fmt1(pct);
        double pctD = pct.doubleValue();

        if (rank <= 15) {
            hint(hints, ctx, "OWID_POVERTY_HIGH",
                    map("pct", pctStr, "rank", ordinal(rank), "total", String.valueOf(total)),
                    HintType.RANKING, Category.DEMOGRAPHICS, amb, 78);
        } else if (rank > total - 20) {
            hint(hints, ctx, "OWID_POVERTY_LOW",
                    map("pct", pctStr),
                    HintType.ABSOLUTE_FACT, Category.DEMOGRAPHICS, amb, 72);
        } else {
            String tier;
            if (pctD > 30.0) tier = "high";
            else if (pctD < 5.0) tier = "low";
            else tier = "moderate";
            hint(hints, ctx, "OWID_POVERTY_FACT",
                    map("pct", pctStr, "tier", tier),
                    HintType.ABSOLUTE_FACT, Category.DEMOGRAPHICS, amb, 70);
        }
        addPercentileHint(hints, ctx, rank, total, "OWID_POVERTY_SHARE", Category.DEMOGRAPHICS, amb, true);
    }

    private void generateCo2(List<CandidateHint> hints, StatisticContext ctx,
                             Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal tonnes = val(stats, "OWID_CO2_PER_CAPITA");
        if (tonnes == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_CO2_PER_CAPITA", List.of());
        int rank = rank(data, "OWID_CO2_PER_CAPITA", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byBracket(tonnes, sorted, 2.0);
        String s = fmt1(tonnes);

        if (rank <= 15) {
            hint(hints, ctx, "OWID_CO2_HIGH",
                    map("value", s, "rank", ordinal(rank)),
                    HintType.RANKING, Category.ENVIRONMENT, Math.max(0, amb - 1), 76);
        } else if (rank > total - 20) {
            hint(hints, ctx, "OWID_CO2_LOW",
                    map("value", s),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, amb, 70);
        } else {
            hint(hints, ctx, "OWID_CO2_FACT",
                    map("value", s, "rank", String.valueOf(rank), "total", String.valueOf(total)),
                    HintType.ABSOLUTE_FACT, Category.ENVIRONMENT, amb, 68);
        }
        BigDecimal avg = data.globalAvg().get("OWID_CO2_PER_CAPITA");
        if (avg != null) {
            String dir = tonnes.compareTo(avg) >= 0 ? "above" : "below";
            String diff = absDiff1(tonnes, avg);
            hint(hints, ctx, "OWID_CO2_VS_AVG",
                    map("value", s, "direction", dir, "avg", fmt1(avg), "diff", diff),
                    HintType.RELATIVE_FACT, Category.ENVIRONMENT, amb + 1, 66);
        }
        hint(hints, ctx, "OWID_CO2_RANK",
                map("rank", String.valueOf(rank), "total", String.valueOf(total), "value", s),
                HintType.RANKING, Category.ENVIRONMENT, OwidAmbiguityCalculator.byRank(sorted), 72);
        addPercentileHint(hints, ctx, rank, total, "OWID_CO2_PER_CAPITA", Category.ENVIRONMENT, amb, false);
    }

    private void generateLifeExpectancy(List<CandidateHint> hints, StatisticContext ctx,
                                        Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal years = val(stats, "OWID_LIFE_EXPECTANCY");
        if (years == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_LIFE_EXPECTANCY", List.of());
        int rank = rank(data, "OWID_LIFE_EXPECTANCY", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byBracket(years, sorted, 2.0);
        String s = fmt1(years);

        if (rank <= 20) {
            hint(hints, ctx, "OWID_LIFE_HIGH",
                    map("value", s, "rank", ordinal(rank)),
                    HintType.RANKING, Category.DEMOGRAPHICS, amb, 66);
        } else if (rank > total - 20) {
            hint(hints, ctx, "OWID_LIFE_LOW",
                    map("value", s, "rank", String.valueOf(rank), "total", String.valueOf(total)),
                    HintType.RANKING, Category.DEMOGRAPHICS, amb, 72);
        } else {
            hint(hints, ctx, "OWID_LIFE_FACT",
                    map("value", s, "rank", String.valueOf(rank), "total", String.valueOf(total)),
                    HintType.ABSOLUTE_FACT, Category.DEMOGRAPHICS, amb, 63);
        }
        BigDecimal avg = data.globalAvg().get("OWID_LIFE_EXPECTANCY");
        if (avg != null) {
            String diff = absDiff1(years, avg);
            String dir = years.compareTo(avg) >= 0 ? "above" : "below";
            hint(hints, ctx, "OWID_LIFE_VS_AVG",
                    map("value", s, "diff", diff, "direction", dir, "avg", fmt1(avg)),
                    HintType.RELATIVE_FACT, Category.DEMOGRAPHICS, amb + 1, 62);
        }
        hint(hints, ctx, "OWID_LIFE_RANK",
                map("rank", String.valueOf(rank), "total", String.valueOf(total), "value", s),
                HintType.RANKING, Category.DEMOGRAPHICS, OwidAmbiguityCalculator.byRank(sorted), 65);
        addPercentileHint(hints, ctx, rank, total, "OWID_LIFE_EXPECTANCY", Category.DEMOGRAPHICS, amb, false);
    }

    private void generateFertility(List<CandidateHint> hints, StatisticContext ctx,
                                   Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal rate = val(stats, "OWID_FERTILITY_RATE");
        if (rate == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_FERTILITY_RATE", List.of());
        int rank = rank(data, "OWID_FERTILITY_RATE", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byBracket(rate, sorted, 0.5);
        String s = fmt1(rate);
        double rateD = rate.doubleValue();

        if (rank <= 15) {
            hint(hints, ctx, "OWID_FERTILITY_HIGH",
                    map("value", s, "rank", ordinal(rank)),
                    HintType.RANKING, Category.DEMOGRAPHICS, amb, 72);
        } else if (rank > total - 15) {
            hint(hints, ctx, "OWID_FERTILITY_LOW",
                    map("value", s),
                    HintType.ABSOLUTE_FACT, Category.DEMOGRAPHICS, amb, 68);
        } else {
            String rel = rateD > 2.1 ? "above" : "below";
            hint(hints, ctx, "OWID_FERTILITY_MID",
                    map("value", s, "direction", rel),
                    HintType.RELATIVE_FACT, Category.DEMOGRAPHICS, amb, 65);
        }
        hint(hints, ctx, "OWID_FERTILITY_RANK",
                map("rank", String.valueOf(rank), "total", String.valueOf(total), "value", s),
                HintType.RANKING, Category.DEMOGRAPHICS, OwidAmbiguityCalculator.byRank(sorted), 68);
        addPercentileHint(hints, ctx, rank, total, "OWID_FERTILITY_RATE", Category.DEMOGRAPHICS, amb, false);
    }

    private void generateInternet(List<CandidateHint> hints, StatisticContext ctx,
                                  Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal pct = val(stats, "OWID_INTERNET_USERS");
        if (pct == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_INTERNET_USERS", List.of());
        int rank = rank(data, "OWID_INTERNET_USERS", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byBracket(pct, sorted, 5.0);
        String s = fmt1(pct);
        if (rank <= 20) {
            hint(hints, ctx, "OWID_INTERNET_HIGH",
                    map("value", s, "rank", ordinal(rank)),
                    HintType.RANKING, Category.CULTURE, amb, 64);
        } else if (rank > total - 20) {
            hint(hints, ctx, "OWID_INTERNET_LOW",
                    map("value", s),
                    HintType.ABSOLUTE_FACT, Category.CULTURE, amb, 70);
        }
        BigDecimal avg = data.globalAvg().get("OWID_INTERNET_USERS");
        if (avg != null) {
            String dir = pct.compareTo(avg) >= 0 ? "above" : "below";
            hint(hints, ctx, "OWID_INTERNET_VS_AVG",
                    map("value", s, "direction", dir, "avg", fmt1(avg)),
                    HintType.RELATIVE_FACT, Category.CULTURE, amb + 1, 60);
        }
        addPercentileHint(hints, ctx, rank, total, "OWID_INTERNET_USERS", Category.CULTURE, amb, false);
    }

    private void generateAlcohol(List<CandidateHint> hints, StatisticContext ctx,
                                 Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal litres = val(stats, "OWID_ALCOHOL_CONSUMPTION");
        if (litres == null) return;
        List<BigDecimal> sorted = data.sortedDesc().getOrDefault("OWID_ALCOHOL_CONSUMPTION", List.of());
        int rank = rank(data, "OWID_ALCOHOL_CONSUMPTION", ctx.statistic().getCountryId());
        int total = sorted.size();
        int amb = OwidAmbiguityCalculator.byBracket(litres, sorted, 2.0);
        String s = fmt1(litres);
        if (rank <= 15) {
            hint(hints, ctx, "OWID_ALCOHOL_HIGH",
                    map("value", s, "rank", ordinal(rank)),
                    HintType.RANKING, Category.HEALTH, Math.max(0, amb - 1), 72);
        } else if (rank > total - 20) {
            hint(hints, ctx, "OWID_ALCOHOL_LOW",
                    map("value", s),
                    HintType.ABSOLUTE_FACT, Category.HEALTH, amb, 68);
        }
        BigDecimal avg = data.globalAvg().get("OWID_ALCOHOL_CONSUMPTION");
        if (avg != null) {
            String dir = litres.compareTo(avg) >= 0 ? "above" : "below";
            hint(hints, ctx, "OWID_ALCOHOL_VS_AVG",
                    map("value", s, "direction", dir, "avg", fmt1(avg)),
                    HintType.RELATIVE_FACT, Category.HEALTH, amb + 1, 64);
        }
        addPercentileHint(hints, ctx, rank, total, "OWID_ALCOHOL_CONSUMPTION", Category.HEALTH, amb, false);
    }

    private void generateCombos(List<CandidateHint> hints, StatisticContext ctx,
                                Map<String, CountryStatistic> stats, OwidCachedData data) {
        BigDecimal happiness = val(stats, "OWID_HAPPINESS");
        BigDecimal renewables = val(stats, "OWID_RENEWABLES_PCT");
        BigDecimal democracy = val(stats, "OWID_DEMOCRACY");
        BigDecimal poverty = val(stats, "OWID_POVERTY_SHARE");
        BigDecimal co2 = val(stats, "OWID_CO2_PER_CAPITA");
        BigDecimal lifeExp = val(stats, "OWID_LIFE_EXPECTANCY");
        BigDecimal fertility = val(stats, "OWID_FERTILITY_RATE");

        if (renewables != null && renewables.doubleValue() >= 60.0) {
            String pctStr = fmt1(renewables);
            if (democracy != null && democracy.doubleValue() >= 0.65) {
                hint(hints, ctx, "OWID_COMBO_GREEN_DEMOCRACY",
                        map("renewables", pctStr, "demo", democracy.setScale(2, RoundingMode.HALF_UP).toPlainString()),
                        HintType.UNIQUE_FEATURE, Category.ENVIRONMENT, 3, 80);
            } else {
                hint(hints, ctx, "OWID_COMBO_GREEN_LEADER",
                        map("renewables", pctStr),
                        HintType.UNIQUE_FEATURE, Category.ENVIRONMENT, 3, 76);
            }
        }

        if (democracy != null && democracy.doubleValue() <= 0.10) {
            hint(hints, ctx, "OWID_COMBO_AUTOCRACY",
                    map("score", democracy.setScale(2, RoundingMode.HALF_UP).toPlainString()),
                    HintType.UNIQUE_FEATURE, Category.POLITICS, 4, 80);
        }

        if (poverty != null && poverty.doubleValue() >= 40.0
                && democracy != null && democracy.doubleValue() <= 0.20) {
            hint(hints, ctx, "OWID_COMBO_POVERTY_AUTOCRACY",
                    map("poverty", fmt1(poverty), "demo", democracy.setScale(2, RoundingMode.HALF_UP).toPlainString()),
                    HintType.UNIQUE_FEATURE, Category.POLITICS, 4, 82);
        }

        if (co2 != null && renewables != null
                && co2.doubleValue() >= 10.0 && renewables.doubleValue() <= 10.0) {
            hint(hints, ctx, "OWID_COMBO_HIGH_CARBON_FOSSIL",
                    map("co2", fmt1(co2), "renewables", fmt1(renewables)),
                    HintType.UNIQUE_FEATURE, Category.ENVIRONMENT, 4, 80);
        }

        if (lifeExp != null && happiness != null
                && lifeExp.doubleValue() >= 80.0 && happiness.doubleValue() >= 6.5) {
            hint(hints, ctx, "OWID_COMBO_LONG_HAPPY",
                    map("life", fmt1(lifeExp), "happiness", fmt1(happiness)),
                    HintType.UNIQUE_FEATURE, Category.DEMOGRAPHICS, 5, 76);
        }

        if (fertility != null && poverty != null
                && fertility.doubleValue() >= 4.5 && poverty.doubleValue() >= 30.0) {
            hint(hints, ctx, "OWID_COMBO_FERTILITY_POVERTY",
                    map("fertility", fmt1(fertility), "poverty", fmt1(poverty)),
                    HintType.UNIQUE_FEATURE, Category.DEMOGRAPHICS, 5, 78);
        }
    }

    private void addPercentileHint(List<CandidateHint> hints, StatisticContext ctx,
                                   int rank, int total, String indicator,
                                   Category category, int baseAmb, boolean lowerIsBetter) {
        if (total < 10) return;
        double pctFromTop = (double) rank / total * 100;
        double pctFromBot = 100.0 - pctFromTop;

        String code;
        Map<String, String> params;
        int pct;
        if (!lowerIsBetter) {
            if (pctFromTop <= 10) {
                pct = (int) Math.ceil(pctFromTop);
                code = "OWID_PERCENTILE_TOP";
                params = map("pct", String.valueOf(Math.max(1, pct)), "indicator", indicatorDisplayName(indicator));
            } else if (pctFromBot <= 10) {
                pct = (int) Math.ceil(pctFromBot);
                code = "OWID_PERCENTILE_BOTTOM";
                params = map("pct", String.valueOf(Math.max(1, pct)), "indicator", indicatorDisplayName(indicator));
            } else return;
        } else {
            if (pctFromTop <= 10) {
                pct = (int) Math.ceil(pctFromTop);
                code = "OWID_PERCENTILE_WORST";
                params = map("pct", String.valueOf(Math.max(1, pct)), "indicator", indicatorDisplayName(indicator));
            } else if (pctFromBot <= 10) {
                pct = (int) Math.ceil(pctFromBot);
                code = "OWID_PERCENTILE_BEST";
                params = map("pct", String.valueOf(Math.max(1, pct)), "indicator", indicatorDisplayName(indicator));
            } else return;
        }
        hint(hints, ctx, code, params, HintType.RANKING, category, Math.max(0, baseAmb - 2), 72);
    }

    private static String indicatorDisplayName(String code) {
        return switch (code) {
            case "OWID_HAPPINESS" -> "life satisfaction";
            case "OWID_HDI" -> "human development";
            case "OWID_DEMOCRACY" -> "liberal democracy";
            case "OWID_SOLAR_PCT" -> "solar energy share";
            case "OWID_WIND_PCT" -> "wind energy share";
            case "OWID_RENEWABLES_PCT" -> "renewable energy";
            case "OWID_POVERTY_SHARE" -> "extreme poverty";
            case "OWID_CO2_PER_CAPITA" -> "per-capita CO₂ emissions";
            case "OWID_LIFE_EXPECTANCY" -> "life expectancy";
            case "OWID_FERTILITY_RATE" -> "fertility rate";
            case "OWID_INTERNET_USERS" -> "internet penetration";
            case "OWID_ALCOHOL_CONSUMPTION" -> "alcohol consumption";
            default -> code.replace("OWID_", "").replace("_", " ").toLowerCase();
        };
    }

    private OwidCachedData loadData() {
        List<CountryStatistic> all = statisticRepository.findBySource(DataSource.OUR_WORLD_IN_DATA);

        Map<Long, Map<String, CountryStatistic>> byCountry = new HashMap<>();
        Map<String, List<CountryStatistic>> byIndicator = new HashMap<>();

        for (CountryStatistic cs : all) {
            byCountry.computeIfAbsent(cs.getCountryId(), k -> new HashMap<>())
                    .put(cs.getIndicatorCode(), cs);
            if (cs.getValue() != null) {
                byIndicator.computeIfAbsent(cs.getIndicatorCode(), k -> new ArrayList<>()).add(cs);
            }
        }

        Map<String, List<BigDecimal>> sortedDesc = new HashMap<>();
        Map<String, BigDecimal> globalAvg = new HashMap<>();
        Map<String, Map<Long, Integer>> ranks = new HashMap<>();

        for (Map.Entry<String, List<CountryStatistic>> e : byIndicator.entrySet()) {
            String indicator = e.getKey();
            List<CountryStatistic> sorted = e.getValue().stream()
                    .sorted(Comparator.comparing(CountryStatistic::getValue).reversed())
                    .toList();

            List<BigDecimal> values = sorted.stream()
                    .map(CountryStatistic::getValue)
                    .toList();
            sortedDesc.put(indicator, values);

            BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            globalAvg.put(indicator, sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP));

            Map<Long, Integer> rankMap = new HashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                rankMap.put(sorted.get(i).getCountryId(), i + 1);
            }
            ranks.put(indicator, rankMap);
        }

        log.info("[OWID] Cache loaded: {} countries, {} indicators", byCountry.size(), sortedDesc.size());
        return new OwidCachedData(byCountry, sortedDesc, globalAvg, ranks, byCountry.size());
    }

    private void hint(List<CandidateHint> hints, StatisticContext ctx, String code,
                      Map<String, ?> params, HintType type, Category category,
                      int ambiguity, int baseDiff) {
        TemplatedHintBuilder.buildDynamicTemplatedCandidate(
                diffCalc, code, code, params, type, category,
                DataSource.OUR_WORLD_IN_DATA, ctx.statistic().getYear(),
                ambiguity, baseDiff, BigDecimal.valueOf(0.80), LENS
        ).ifPresent(hints::add);
    }

    private BigDecimal val(Map<String, CountryStatistic> stats, String indicator) {
        CountryStatistic cs = stats.get(indicator);
        return cs != null ? cs.getValue() : null;
    }

    private int rank(OwidCachedData data, String indicator, Long countryId) {
        return data.ranks().getOrDefault(indicator, Map.of()).getOrDefault(countryId, 999);
    }

    private int year(StatisticContext ctx) {
        return ctx.statistic().getYear() != null ? ctx.statistic().getYear() : 2024;
    }

    private String fmt1(BigDecimal v) {
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private String absDiff1(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static String ordinal(int n) {
        if (n >= 11 && n <= 13) return n + "th";
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }
}
