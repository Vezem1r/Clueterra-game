package io.clueterra.questions.clients.bigmac.hintgeneration;

import io.clueterra.questions.clients.bigmac.BigMacConstants;
import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.CountryRepository;
import io.clueterra.questions.clients.common.ExpiringLensCache;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.IndicatorConfig;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class BigMacLens implements HintLens {

    private static final Map<String, HintType> HINT_TYPES = Map.ofEntries(
            Map.entry("BIGMAC_PRICE_STORY", HintType.ABSOLUTE_FACT),
            Map.entry("BIGMAC_CHEAPEST_RANK", HintType.RANKING),
            Map.entry("BIGMAC_EXPENSIVE_RANK", HintType.RANKING),
            Map.entry("BIGMAC_PPP_INSIGHT", HintType.RELATIVE_FACT),
            Map.entry("BIGMAC_EXTREME_CHEAP", HintType.SUPERLATIVE),
            Map.entry("BIGMAC_EXTREME_EXPENSIVE", HintType.SUPERLATIVE),
            Map.entry("BIGMAC_REGIONAL_CHEAPEST", HintType.RANKING),
            Map.entry("BIGMAC_REGIONAL_EXPENSIVE", HintType.RANKING),
            Map.entry("BIGMAC_REGIONAL_OUTLIER", HintType.RELATIVE_FACT),
            Map.entry("BIGMAC_PRICE_RATIO", HintType.RELATIVE_FACT),
            Map.entry("BIGMAC_CURRENCY_EXTREME", HintType.SUPERLATIVE),
            Map.entry("BIGMAC_DOLLAR_PARITY", HintType.UNIQUE_FEATURE),
            Map.entry("BIGMAC_SURPRISE_MATCH", HintType.UNIQUE_FEATURE),
            Map.entry("BIGMAC_VS_USA", HintType.RELATIVE_FACT)
    );

    private final StatisticRepository statisticRepository;
    private final CountryRepository countryRepository;

    private final ExpiringLensCache<BigMacCachedData> cache = new ExpiringLensCache<>(Duration.ofMinutes(10));

    @Override
    public String name() {
        return "big-mac";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("BIG_MAC_", "BIGMAC_");
    }

    @Override
    public boolean canApply(StatisticContext context) {
        return BigMacConstants.PRICE_INDICATOR.equals(context.statistic().getIndicatorCode());
    }

    @Override
    public List<CandidateHint> generate(StatisticContext context) {
        List<CandidateHint> hints = new ArrayList<>();

        BigMacCachedData data = cache.get(this::loadData);
        if (data.totalCountries() == 0) return hints;

        Long countryId = context.statistic().getCountryId();
        List<CountryStatistic> countryStats = data.byCountry().getOrDefault(countryId, List.of());

        BigDecimal price = findValue(countryStats, BigMacConstants.PRICE_INDICATOR);
        BigDecimal valuation = findValue(countryStats, BigMacConstants.VALUATION_INDICATOR);

        if (price == null) return hints;

        String priceStr = "$" + price.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String avgStr = "$" + data.globalAvg().setScale(2, RoundingMode.HALF_UP).toPlainString();
        int total = data.totalCountries();

        String comparison = BigMacConstants.comparisonText(price, data.globalAvg());
        addTemplateHint(hints, context, "BIGMAC_PRICE_STORY",
                Map.of("price", priceStr, "comparison", comparison, "avg", avgStr),
                BigMacAmbiguityCalculator.priceAmbiguity(price, data.sortedPrices()));

        int rank = computeRank(price, data.sortedPrices());
        int reverseRank = total - rank + 1;

        double percentile = (double) rank / total;
        if (percentile <= BigMacConstants.CHEAP_PERCENTILE) {
            addTemplateHint(hints, context, "BIGMAC_CHEAPEST_RANK",
                    Map.of("total", String.valueOf(total), "rank", String.valueOf(rank), "price", priceStr),
                    BigMacAmbiguityCalculator.rankAmbiguity(price, data.sortedPrices()));
        }

        double expPercentile = (double) reverseRank / total;
        if (expPercentile <= BigMacConstants.CHEAP_PERCENTILE) {
            addTemplateHint(hints, context, "BIGMAC_EXPENSIVE_RANK",
                    Map.of("total", String.valueOf(total), "rank", String.valueOf(reverseRank), "price", priceStr),
                    BigMacAmbiguityCalculator.rankAmbiguity(price, data.sortedPrices()));
        }

        String valuationPctStr = valuation != null
                ? valuation.abs().setScale(1, RoundingMode.HALF_UP).toPlainString()
                : null;

        if (valuation != null && valuation.abs().doubleValue() >= 1.0) {
            String stretchText = BigMacConstants.stretchText(valuation);
            String moreLess = BigMacConstants.moreLessText(valuation);
            addTemplateHint(hints, context, "BIGMAC_PPP_INSIGHT",
                    Map.of("stretch_text", stretchText, "pct", valuationPctStr, "more_less", moreLess),
                    BigMacAmbiguityCalculator.pppAmbiguity(valuation, data.allValuations()));
        }

        if (rank <= BigMacConstants.TOP_BOTTOM_N) {
            addTemplateHint(hints, context, "BIGMAC_EXTREME_CHEAP",
                    Map.of("price", priceStr),
                    BigMacAmbiguityCalculator.priceAmbiguity(price, data.sortedPrices()));
        }
        if (reverseRank <= BigMacConstants.TOP_BOTTOM_N) {
            String ratioCheapest = data.cheapestPrice() != null
                    && data.cheapestPrice().compareTo(BigDecimal.ZERO) > 0
                    ? price.divide(data.cheapestPrice(), 1, RoundingMode.HALF_UP).toPlainString()
                    : "several";
            addTemplateHint(hints, context, "BIGMAC_EXTREME_EXPENSIVE",
                    Map.of("price", priceStr, "ratio_cheapest", ratioCheapest),
                    BigMacAmbiguityCalculator.priceAmbiguity(price, data.sortedPrices()));
        }

        String region = data.countryIdToRegion().getOrDefault(countryId, context.region());
        List<BigDecimal> regionPrices = region != null
                ? data.pricesByRegion().getOrDefault(region, List.of())
                : List.of();
        BigDecimal regAvg = region != null ? data.regionalAvg().get(region) : null;

        if (region != null && regionPrices.size() >= BigMacConstants.MIN_REGION_SIZE) {
            BigDecimal regionMin = regionPrices.getFirst();
            BigDecimal regionMax = regionPrices.getLast();

            if (price.compareTo(regionMin) == 0) {
                addTemplateHint(hints, context, "BIGMAC_REGIONAL_CHEAPEST",
                        Map.of("region", region, "price", priceStr), 0);
            }
            if (price.compareTo(regionMax) == 0) {
                addTemplateHint(hints, context, "BIGMAC_REGIONAL_EXPENSIVE",
                        Map.of("region", region, "price", priceStr), 0);
            }
        }

        if (region != null && regAvg != null && regAvg.compareTo(BigDecimal.ZERO) != 0
                && regionPrices.size() >= BigMacConstants.MIN_REGION_SIZE) {
            double deviationPct = price.subtract(regAvg).doubleValue() / regAvg.doubleValue() * 100;
            if (Math.abs(deviationPct) >= BigMacConstants.REGIONAL_OUTLIER_THRESHOLD * 100) {
                String direction = deviationPct > 0 ? "above" : "below";
                String devStr = BigDecimal.valueOf(Math.abs(deviationPct))
                        .setScale(0, RoundingMode.HALF_UP).toPlainString();
                String vibe = BigMacConstants.outlierVibe(direction);
                int ambiguity = BigMacAmbiguityCalculator.regionalOutlierAmbiguity(
                        deviationPct, regionPrices, regAvg);
                addTemplateHint(hints, context, "BIGMAC_REGIONAL_OUTLIER",
                        Map.of("price", priceStr, "deviation", devStr, "direction", direction,
                                "region", region, "vibe", vibe),
                        ambiguity);
            }
        }

        if (data.cheapestPrice() != null && data.cheapestPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = price.divide(data.cheapestPrice(), 4, RoundingMode.HALF_UP);
            if (ratio.doubleValue() >= BigMacConstants.PRICE_RATIO_THRESHOLD) {
                String ratioStr = ratio.setScale(1, RoundingMode.HALF_UP).toPlainString();
                int ambiguity = BigMacAmbiguityCalculator.priceRatioAmbiguity(
                        ratio, data.sortedPrices(), data.cheapestPrice());
                addTemplateHint(hints, context, "BIGMAC_PRICE_RATIO",
                        Map.of("ratio", ratioStr), ambiguity);
            }
        }

        if (valuation != null && !data.sortedValuationAbs().isEmpty()) {
            int extremeN = BigMacConstants.CURRENCY_EXTREME_N;
            List<BigDecimal> topAbs = data.sortedValuationAbs();
            if (topAbs.size() >= extremeN) {
                BigDecimal threshold = topAbs.get(topAbs.size() - extremeN);
                if (valuation.abs().compareTo(threshold) >= 0) {
                    boolean overvalued = valuation.compareTo(BigDecimal.ZERO) > 0;
                    String overUnder = overvalued ? "over" : "under";
                    String aboveBelow = overvalued ? "above" : "below";
                    String takeaway = BigMacConstants.currencyTakeaway(overUnder);
                    addTemplateHint(hints, context, "BIGMAC_CURRENCY_EXTREME",
                            Map.of("direction", overUnder, "pct", valuationPctStr,
                                    "above_below", aboveBelow, "takeaway", takeaway),
                            BigMacAmbiguityCalculator.pppAmbiguity(valuation, data.allValuations()));
                }
            }
        }

        if (valuation != null && data.usPrice() != null
                && !"US".equals(context.countryCode())
                && valuation.abs().doubleValue() <= BigMacConstants.DOLLAR_PARITY_THRESHOLD) {
            String usPriceStr = "$" + data.usPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
            int ambiguity = BigMacAmbiguityCalculator.dollarParityAmbiguity(data.allValuations());
            addTemplateHint(hints, context, "BIGMAC_DOLLAR_PARITY",
                    Map.of("price", priceStr, "us_price", usPriceStr), ambiguity);
        }

        if (data.usPrice() != null && !"US".equals(context.countryCode())) {
            double diffPct = price.subtract(data.usPrice()).doubleValue()
                    / data.usPrice().doubleValue() * 100;
            if (Math.abs(diffPct) >= BigMacConstants.VS_USA_THRESHOLD) {
                boolean moreExpensive = diffPct > 0;
                String diffPctStr = BigDecimal.valueOf(Math.abs(diffPct))
                        .setScale(0, RoundingMode.HALF_UP).toPlainString();
                String cheaperMore = moreExpensive ? "more expensive" : "cheaper";
                String vsQuip = BigMacConstants.vsUsaQuip(moreExpensive);
                String usPriceStr = "$" + data.usPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
                addTemplateHint(hints, context, BigMacConstants.VS_USA_INDICATOR,
                        Map.of("price", priceStr, "us_price", usPriceStr,
                                "diff_pct", diffPctStr, "cheaper_more", cheaperMore, "vs_quip", vsQuip),
                        BigMacAmbiguityCalculator.vsUsaAmbiguity(diffPct, data.sortedPrices(), data.usPrice()));
            }
        }

        generateSurpriseMatch(context, data, countryId, price, priceStr, region, hints);

        return hints;
    }

    private void generateSurpriseMatch(StatisticContext context, BigMacCachedData data,
                                        Long countryId, BigDecimal price, String priceStr,
                                        String region, List<CandidateHint> hints) {
        if (region == null) return;
        String price2d = price.setScale(2, RoundingMode.HALF_UP).toPlainString();
        data.countryIdToPrice().entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString().equals(price2d))
                .filter(e -> !region.equals(data.countryIdToRegion().get(e.getKey())))
                .map(e -> data.countryIdToRegion().getOrDefault(e.getKey(), "another region"))
                .findFirst()
                .ifPresent(matchRegion -> addTemplateHint(hints, context, "BIGMAC_SURPRISE_MATCH",
                        Map.of("price", priceStr, "match_region", matchRegion), 1));
    }

    @Override
    public void invalidateCache() {
        cache.invalidate();
    }

    private BigMacCachedData loadData() {
        List<CountryStatistic> all = statisticRepository.findBySource(DataSource.BIG_MAC_INDEX);
        Map<Long, List<CountryStatistic>> byCountry = new HashMap<>();
        List<BigDecimal> prices = new ArrayList<>();
        List<BigDecimal> valuations = new ArrayList<>();
        Map<Long, BigDecimal> countryIdToPrice = new HashMap<>();
        Map<Long, BigDecimal> countryIdToValuation = new HashMap<>();

        for (CountryStatistic stat : all) {
            byCountry.computeIfAbsent(stat.getCountryId(), k -> new ArrayList<>()).add(stat);
            if (BigMacConstants.PRICE_INDICATOR.equals(stat.getIndicatorCode()) && stat.getValue() != null) {
                prices.add(stat.getValue());
                countryIdToPrice.put(stat.getCountryId(), stat.getValue());
            }
            if (BigMacConstants.VALUATION_INDICATOR.equals(stat.getIndicatorCode()) && stat.getValue() != null) {
                valuations.add(stat.getValue());
                countryIdToValuation.put(stat.getCountryId(), stat.getValue());
            }
        }

        prices.sort(Comparator.naturalOrder());
        BigDecimal avg = prices.isEmpty() ? BigDecimal.ZERO
                : prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);

        BigDecimal cheapestPrice = prices.isEmpty() ? null : prices.getFirst();

        Map<Long, String> countryIdToRegion = countryRepository.findAllCountryRegions();
        BigDecimal usPrice = resolveUsPrice(byCountry);

        Map<String, List<BigDecimal>> pricesByRegion = new HashMap<>();
        for (var entry : countryIdToPrice.entrySet()) {
            String region = countryIdToRegion.get(entry.getKey());
            if (region != null) {
                pricesByRegion.computeIfAbsent(region, k -> new ArrayList<>()).add(entry.getValue());
            }
        }
        pricesByRegion.values().forEach(list -> list.sort(Comparator.naturalOrder()));

        Map<String, BigDecimal> regionalAvg = new HashMap<>();
        for (var entry : pricesByRegion.entrySet()) {
            List<BigDecimal> rp = entry.getValue();
            BigDecimal sum = rp.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            regionalAvg.put(entry.getKey(),
                    sum.divide(BigDecimal.valueOf(rp.size()), 2, RoundingMode.HALF_UP));
        }

        List<BigDecimal> sortedValuationAbs = valuations.stream()
                .map(BigDecimal::abs)
                .sorted(Comparator.naturalOrder())
                .toList();

        return new BigMacCachedData(
                byCountry, prices, valuations, avg, prices.size(),
                pricesByRegion, regionalAvg, cheapestPrice, usPrice,
                countryIdToRegion, countryIdToPrice, countryIdToValuation,
                sortedValuationAbs);
    }

    private BigDecimal resolveUsPrice(Map<Long, List<CountryStatistic>> byCountry) {
        var usCountry = countryRepository.findByIsoAlpha2("US");
        if (usCountry.isEmpty()) return null;
        Long usId = usCountry.get().getId();
        List<CountryStatistic> usStats = byCountry.getOrDefault(usId, List.of());
        return usStats.stream()
                .filter(s -> BigMacConstants.PRICE_INDICATOR.equals(s.getIndicatorCode()))
                .map(CountryStatistic::getValue)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal findValue(List<CountryStatistic> stats, String indicatorCode) {
        return stats.stream()
                .filter(s -> indicatorCode.equals(s.getIndicatorCode()))
                .map(CountryStatistic::getValue)
                .findFirst()
                .orElse(null);
    }

    private int computeRank(BigDecimal price, List<BigDecimal> sortedPrices) {
        int rank = 1;
        for (BigDecimal p : sortedPrices) {
            if (p.compareTo(price) < 0) rank++;
            else break;
        }
        return rank;
    }

    private void addTemplateHint(List<CandidateHint> hints, StatisticContext ctx,
                                  String indicatorCode, Map<String, String> params, int ambiguity) {
        if (!IndicatorConfig.hasTemplate(indicatorCode)) {
            log.warn("[BigMac] No template for indicator: {}", indicatorCode);
            return;
        }
        HintType hintType = HINT_TYPES.getOrDefault(indicatorCode, HintType.ABSOLUTE_FACT);
        TemplatedHintBuilder.buildConfiguredStaticTemplatedCandidate(
                ctx,
                indicatorCode,
                params,
                hintType,
                ambiguity,
                35,
                BigDecimal.valueOf(0.70),
                name()
        ).ifPresent(hints::add);
    }
}
