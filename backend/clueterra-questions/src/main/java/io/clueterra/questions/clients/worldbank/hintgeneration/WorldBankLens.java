package io.clueterra.questions.clients.worldbank.hintgeneration;

import io.clueterra.questions.clients.common.AbstractCachedLens;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class WorldBankLens extends AbstractCachedLens {

    private static final Set<String> OWNED_INDICATORS = Set.of(
            "SP.POP.TOTL",
            "NY.GDP.MKTP.CD",
            "NY.GDP.PCAP.CD",
            "SP.DYN.LE00.IN",
            "EN.POP.DNST",
            "IT.NET.USER.ZS",
            "EG.FEC.RNEW.ZS",
            "NE.TRD.GNFS.ZS",
            "NV.SRV.TOTL.ZS",
            "NV.AGR.TOTL.ZS",
            "SP.POP.MED",
            "MS.MIL.XPND.GD.ZS",
            "SP.URB.TOTL.IN.ZS",
            "SP.DYN.TFRT.IN",
            "AG.LND.FRST.ZS",
            "EG.ELC.ACCS.ZS",
            "IT.CEL.SETS.P2",
            "SP.POP.GROW",
            "SP.POP.0014.TO.ZS",
            "SP.POP.65UP.TO.ZS",
            "SM.POP.NETM",
            "NY.GDP.MKTP.KD.ZG",
            "FP.CPI.TOTL.ZG",
            "SL.UEM.TOTL.ZS",
            "GC.DOD.TOTL.GD.ZS",
            "SI.POV.GINI",
            "NV.IND.TOTL.ZS",
            "NY.GDP.TOTL.RT.ZS",
            "ST.INT.RCPT.CD",
            "FI.RES.TOTL.CD",
            "BX.TRF.PWKR.CD.DT",
            "SH.DYN.MORT",
            "SH.MED.PHYS.ZS",
            "SH.ALC.PCAP.LI",
            "SH.XPD.CHEX.GD.ZS",
            "SH.DYN.AIDS.ZS",
            "SE.ADT.LITR.ZS",
            "SE.XPD.TOTL.GD.ZS",
            "SE.TER.ENRR",
            "EN.ATM.PM25.MC.M3",
            "ER.H2O.FWTL.ZS",
            "EG.ELC.RNEW.ZS",
            "IT.NET.BBND.P2",
            "AG.LND.TOTL.K2",
            "AG.LND.AGRI.ZS"
    );

    private final StatisticRepository statisticRepository;
    private final DynamicDifficultyCalculator difficultyCalculator;
    private final WorldBankAmbiguityCalculator ambiguityCalculator;
    private final AtomicReference<Map<Long, List<CountryStatistic>>> cachedByCountry = new AtomicReference<>(Map.of());

    public WorldBankLens(StatisticRepository statisticRepository,
                         DynamicDifficultyCalculator difficultyCalculator,
                         WorldBankAmbiguityCalculator ambiguityCalculator) {
        this.statisticRepository = statisticRepository;
        this.difficultyCalculator = difficultyCalculator;
        this.ambiguityCalculator = ambiguityCalculator;
    }

    @Override
    public String name() {
        return "world-bank";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("WB_");
    }

    @Override
    public Set<String> claimedIndicators() {
        return OWNED_INDICATORS;
    }

    @Override
    public Set<DataSource> claimedSources() {
        return Set.of(DataSource.WORLD_BANK);
    }


    @Override
    public boolean canApply(StatisticContext ctx) {
        return ctx.statistic().getSource() == DataSource.WORLD_BANK;
    }

    @Override
    protected void loadCache() {
        List<CountryStatistic> all = statisticRepository.findBySource(DataSource.WORLD_BANK);
        Map<Long, List<CountryStatistic>> byCountry = new HashMap<>();
        for (CountryStatistic stat : all) {
            byCountry.computeIfAbsent(stat.getCountryId(), k -> new ArrayList<>()).add(stat);
        }
        cachedByCountry.set(byCountry);
        log.info("[WorldBank] Cache refreshed: {} countries", byCountry.size());
    }

    @Override
    protected List<CandidateHint> doGenerate(StatisticContext ctx) {
        return switch (ctx.statistic().getIndicatorCode()) {
            case "SP.POP.TOTL" -> generatePopulationHint(ctx);
            case "NY.GDP.MKTP.CD" -> generateGdpHint(ctx);
            case "NY.GDP.PCAP.CD" -> generateGdpPerCapitaHint(ctx);
            case "SP.DYN.LE00.IN" -> generateLifeExpectancyHint(ctx);
            case "EN.POP.DNST" -> generateDensityHint(ctx);
            case "IT.NET.USER.ZS" -> generateInternetHint(ctx);
            case "EG.FEC.RNEW.ZS" -> generateRenewableHint(ctx);
            case "NE.TRD.GNFS.ZS" -> generateTradeHint(ctx);
            case "NV.SRV.TOTL.ZS" -> generateServiceEconomyHint(ctx);
            case "NV.AGR.TOTL.ZS" -> generateAgricultureHint(ctx);
            case "SP.POP.MED" -> generateMedianAgeHint(ctx);
            case "MS.MIL.XPND.GD.ZS" -> generateMilitaryHint(ctx);
            case "SP.URB.TOTL.IN.ZS" -> generateUrbanizationHint(ctx);
            case "SP.DYN.TFRT.IN" -> generateFertilityHint(ctx);
            case "AG.LND.FRST.ZS" -> generateForestHint(ctx);
            case "EG.ELC.ACCS.ZS" -> generateElectricityHint(ctx);
            case "IT.CEL.SETS.P2" -> generateMobileHint(ctx);
            case "SP.POP.GROW" -> generatePopulationGrowthHint(ctx);
            case "SP.POP.0014.TO.ZS" -> generateYouthBulgeHint(ctx);
            case "SP.POP.65UP.TO.ZS" -> generateElderlyHint(ctx);
            case "SM.POP.NETM" -> generateNetMigrationHint(ctx);
            case "NY.GDP.MKTP.KD.ZG" -> generateGdpGrowthHint(ctx);
            case "FP.CPI.TOTL.ZG" -> generateInflationHint(ctx);
            case "SL.UEM.TOTL.ZS" -> generateUnemploymentHint(ctx);
            case "GC.DOD.TOTL.GD.ZS" -> generateDebtHint(ctx);
            case "SI.POV.GINI" -> generateGiniHint(ctx);
            case "NV.IND.TOTL.ZS" -> generateIndustryHint(ctx);
            case "NY.GDP.TOTL.RT.ZS" -> generateResourceRentsHint(ctx);
            case "ST.INT.RCPT.CD" -> generateTourismGiantHint(ctx);
            case "FI.RES.TOTL.CD" -> generateForexReservesHint(ctx);
            case "BX.TRF.PWKR.CD.DT" -> generateRemittancesHint(ctx);
            case "SH.DYN.MORT" -> generateInfantMortalityHint(ctx);
            case "SH.MED.PHYS.ZS" -> generatePhysiciansHint(ctx);
            case "SH.ALC.PCAP.LI" -> generateAlcoholHint(ctx);
            case "SH.XPD.CHEX.GD.ZS" -> generateHealthSpendingHint(ctx);
            case "SH.DYN.AIDS.ZS" -> generateHivHint(ctx);
            case "SE.ADT.LITR.ZS" -> generateLiteracyHint(ctx);
            case "SE.XPD.TOTL.GD.ZS" -> generateEducationSpendingHint(ctx);
            case "SE.TER.ENRR" -> generateTertiaryEnrollmentHint(ctx);
            case "EN.ATM.PM25.MC.M3" -> generateAirPollutionHint(ctx);
            case "ER.H2O.FWTL.ZS" -> generateWaterStressHint(ctx);
            case "EG.ELC.RNEW.ZS" -> generateRenewableElectricityHint(ctx);
            case "IT.NET.BBND.P2" -> generateBroadbandHint(ctx);
            case "AG.LND.TOTL.K2" -> generateLandAreaHint(ctx);
            case "AG.LND.AGRI.ZS" -> generateAgriLandHint(ctx);
            default -> List.of();
        };
    }

    private Map<Long, List<CountryStatistic>> byCountry() {
        return cachedByCountry.get();
    }

    private Map<String, BigDecimal> numericMapForCountry(Long countryId) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (CountryStatistic stat : byCountry().getOrDefault(countryId, List.of())) {
            if (stat.getValue() != null) {
                map.put(stat.getIndicatorCode(), stat.getValue());
            }
        }
        return map;
    }

    private List<CandidateHint> generatePopulationHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        List<CandidateHint> hints = new ArrayList<>();
        Long countryId = ctx.statistic().getCountryId();

        if (ctx.hasGlobalRank() && ctx.globalRank() <= 10
                && ctx.statistic().getValue().compareTo(BigDecimal.valueOf(50_000_000)) >= 0) {
            int ambiguity = ambiguityCalculator.computePopulationGiant(ctx.globalRank());
            buildSingleHint(ctx, "WB_POPULATION_GIANT_", "population-giant",
                    Map.of("population", formatPopulation(ctx.statistic().getValue()),
                            "ordinal", ordinal(ctx.globalRank())),
                    HintType.RANKING, ambiguity, 18, BigDecimal.valueOf(0.91)).ifPresent(hints::add);
        }

        Map<String, BigDecimal> stats = numericMapForCountry(countryId);

        addComboHint(hints, generateProsperityCombo(ctx, stats, countryId));
        addComboHint(hints, generateDenseGiantCombo(ctx, stats, countryId));
        addComboHint(hints, generateOpenServiceCombo(ctx, stats, countryId));
        addComboHint(hints, generateGreenConnectedCombo(ctx, stats, countryId));
        addComboHint(hints, generateYoungAgriCombo(ctx, stats, countryId));

        return hints;
    }

    private List<CandidateHint> generateGdpHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || !ctx.hasGlobalRank() || ctx.globalRank() > 10
                || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(500_000_000_000L)) < 0) {
            return List.of();
        }

        int ambiguity = ambiguityCalculator.computeGdpHeavyweight(ctx.globalRank());
        return buildSingleHint(ctx, "WB_GDP_HEAVYWEIGHT_", "gdp-heavyweight",
                Map.of("value", formatUsdCompact(ctx.statistic().getValue()),
                        "ordinal", ordinal(ctx.globalRank())),
                HintType.RANKING, ambiguity, 18, BigDecimal.valueOf(0.90))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateGdpPerCapitaHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || !ctx.hasGlobalRank() || ctx.globalRank() > 12 ||
                ctx.statistic().getValue().compareTo(BigDecimal.valueOf(30000)) < 0) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeGdpPerCapita(ctx.statistic().getValue(), byCountry(), countryId);

        return buildSingleHint(ctx, "WB_GDP_PER_CAPITA_ELITE_", "gdp-per-capita-elite",
                Map.of("value", formatUsdPerPerson(ctx.statistic().getValue()),
                        "ordinal", ordinal(ctx.globalRank())),
                HintType.RANKING, ambiguity, 20, BigDecimal.valueOf(0.89))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateLifeExpectancyHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        boolean qualifies = value.compareTo(BigDecimal.valueOf(81)) >= 0 ||
                (ctx.hasGlobalRank() && ctx.globalRank() <= 10 && value.compareTo(BigDecimal.valueOf(79)) >= 0);
        if (!qualifies) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeLifeExpectancy(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_LONGEVITY_", "longevity",
                Map.of("value", formatDecimal(value, 1)),
                HintType.ABSOLUTE_FACT, ambiguity, 18, BigDecimal.valueOf(0.88))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateDensityHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        boolean qualifies = value.compareTo(BigDecimal.valueOf(500)) >= 0 ||
                (ctx.hasGlobalRank() && ctx.globalRank() <= 8 && value.compareTo(BigDecimal.valueOf(300)) >= 0);
        if (!qualifies) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeDensity(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_DENSITY_STACK_", "density-stack",
                Map.of("value", formatDecimal(value, 0)),
                HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateInternetHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        boolean qualifies = value.compareTo(BigDecimal.valueOf(94)) >= 0 ||
                (ctx.hasGlobalRank() && ctx.globalRank() <= 10 && value.compareTo(BigDecimal.valueOf(88)) >= 0);
        if (!qualifies) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeInternet(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_ONLINE_SATURATION_", "online-saturation",
                Map.of("value", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateRenewableHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        boolean qualifies = value.compareTo(BigDecimal.valueOf(50)) >= 0 ||
                (ctx.hasGlobalRank() && ctx.globalRank() <= 12 && value.compareTo(BigDecimal.valueOf(35)) >= 0);
        if (!qualifies) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeRenewable(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_RENEWABLE_HEAVY_", "renewable-heavy",
                Map.of("value", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.87))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateTradeHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        boolean qualifies = value.compareTo(BigDecimal.valueOf(120)) >= 0 ||
                (ctx.hasGlobalRank() && ctx.globalRank() <= 10 && value.compareTo(BigDecimal.valueOf(100)) >= 0);
        if (!qualifies) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeTrade(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_TRADE_OPEN_", "trade-open",
                Map.of("value", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateServiceEconomyHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(78)) < 0) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeService(ctx.statistic().getValue(), byCountry(), countryId);

        return buildSingleHint(ctx, "WB_SERVICE_ECONOMY_", "service-economy",
                Map.of("value", formatPercent(ctx.statistic().getValue())),
                HintType.RELATIVE_FACT, ambiguity, 24, BigDecimal.valueOf(0.84))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateAgricultureHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(25)) < 0) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeAgriculture(ctx.statistic().getValue(), byCountry(), countryId);

        return buildSingleHint(ctx, "WB_AGRI_HEAVY_", "agri-heavy",
                Map.of("value", formatPercent(ctx.statistic().getValue())),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.86))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateMedianAgeHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();

        if (value.compareTo(BigDecimal.valueOf(21)) <= 0) {
            int ambiguity = ambiguityCalculator.computeMedianAge(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_YOUNG_PROFILE_", "young-profile",
                    Map.of("value", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.87))
                    .map(List::of).orElseGet(List::of);
        }
        if (value.compareTo(BigDecimal.valueOf(42)) >= 0) {
            int ambiguity = ambiguityCalculator.computeMedianAge(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_AGING_PROFILE_", "aging-profile",
                    Map.of("value", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.87))
                    .map(List::of).orElseGet(List::of);
        }
        return List.of();
    }

    private List<CandidateHint> generateMilitaryHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        boolean qualifies = value.compareTo(BigDecimal.valueOf(4)) >= 0 ||
                (ctx.hasGlobalRank() && ctx.globalRank() <= 8 && value.compareTo(BigDecimal.valueOf(3)) >= 0);
        if (!qualifies) {
            return List.of();
        }

        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeMilitary(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_MILITARY_HEAVY_", "military-heavy",
                Map.of("value", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateUrbanizationHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        List<CandidateHint> hints = new ArrayList<>();

        if (value.compareTo(BigDecimal.valueOf(85)) >= 0) {
            int ambiguity = ambiguityCalculator.computeUrbanization(value, byCountry(), countryId);
            buildSingleHint(ctx, "WB_URBAN_DOMINANT_", "urban-dominant",
                    Map.of("pct", formatPercent(value)),
                    HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85)).ifPresent(hints::add);
        }

        addComboHint(hints, generateConcreteJungleCombo(ctx, countryId));
        return hints;
    }

    private List<CandidateHint> generateFertilityHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        List<CandidateHint> hints = new ArrayList<>();

        if (value.compareTo(BigDecimal.valueOf(4.5)) >= 0) {
            int ambiguity = ambiguityCalculator.computeFertility(value, byCountry(), countryId);
            buildSingleHint(ctx, "WB_FERTILITY_HIGH_", "fertility-high",
                    Map.of("rate", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.86)).ifPresent(hints::add);
        }

        if (value.compareTo(BigDecimal.valueOf(1.3)) <= 0) {
            int ambiguity = ambiguityCalculator.computeFertility(value, byCountry(), countryId);
            buildSingleHint(ctx, "WB_FERTILITY_LOW_", "fertility-low",
                    Map.of("rate", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.86)).ifPresent(hints::add);
        }

        addComboHint(hints, generateBabyBoomCombo(ctx, countryId));
        return hints;
    }

    private List<CandidateHint> generateForestHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(60)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeForest(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_FOREST_HEAVY_", "forest-heavy",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.86))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateElectricityHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        List<CandidateHint> hints = new ArrayList<>();

        if (value.compareTo(BigDecimal.valueOf(50)) < 0) {
            int ambiguity = ambiguityCalculator.computeElectricity(value, byCountry(), countryId);
            buildSingleHint(ctx, "WB_ELECTRICITY_GAP_", "electricity-gap",
                    Map.of("pct", formatPercent(value)),
                    HintType.RELATIVE_FACT, ambiguity, 20, BigDecimal.valueOf(0.87)).ifPresent(hints::add);
        }

        addComboHint(hints, generateDarkRuralCombo(ctx, countryId));
        return hints;
    }

    private List<CandidateHint> generateMobileHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(150)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeMobile(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_MOBILE_SATURATED_", "mobile-saturated",
                Map.of("count", formatDecimal(value, 0)),
                HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generatePopulationGrowthHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();

        if (value.compareTo(BigDecimal.valueOf(2.5)) >= 0) {
            int ambiguity = ambiguityCalculator.computePopGrowth(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_POP_GROWTH_BOOM_", "pop-growth-boom",
                    Map.of("rate", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                    .map(List::of).orElseGet(List::of);
        }

        if (value.compareTo(BigDecimal.valueOf(-0.5)) <= 0) {
            int ambiguity = ambiguityCalculator.computePopGrowth(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_POP_GROWTH_SHRINK_", "pop-growth-shrink",
                    Map.of("rate", formatDecimal(value.abs(), 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                    .map(List::of).orElseGet(List::of);
        }

        return List.of();
    }

    private Optional<CandidateHint> generateProsperityCombo(StatisticContext ctx,
                                                             Map<String, BigDecimal> stats,
                                                             Long countryId) {
        BigDecimal gdpPerCapita = stats.get("NY.GDP.PCAP.CD");
        BigDecimal internet = stats.get("IT.NET.USER.ZS");
        BigDecimal longevity = stats.get("SP.DYN.LE00.IN");
        if (gdpPerCapita == null || internet == null || longevity == null) {
            return Optional.empty();
        }
        if (gdpPerCapita.compareTo(BigDecimal.valueOf(30000)) < 0
                || internet.compareTo(BigDecimal.valueOf(85)) < 0
                || longevity.compareTo(BigDecimal.valueOf(78)) < 0) {
            return Optional.empty();
        }

        int ambiguity = ambiguityCalculator.computeCombo(
                ambiguityCalculator.computeGdpPerCapita(gdpPerCapita, byCountry(), countryId),
                ambiguityCalculator.computeInternet(internet, byCountry(), countryId),
                ambiguityCalculator.computeLifeExpectancy(longevity, byCountry(), countryId)
        );

        return buildComboHint(ctx, "WB_PROSPERITY_STACK_", "prosperity-stack",
                Map.of("gdp_per_capita", formatUsdPerPerson(gdpPerCapita),
                        "internet_share", formatPercent(internet),
                        "life_expectancy", formatDecimal(longevity, 1)),
                Category.ECONOMY, HintType.RELATIVE_FACT, ambiguity, 18, BigDecimal.valueOf(0.92));
    }

    private Optional<CandidateHint> generateDenseGiantCombo(StatisticContext ctx,
                                                             Map<String, BigDecimal> stats,
                                                             Long countryId) {
        BigDecimal density = stats.get("EN.POP.DNST");
        if (density == null || !ctx.hasGlobalRank() || ctx.globalRank() > 10
                || density.compareTo(BigDecimal.valueOf(200)) < 0
                || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(50_000_000)) < 0) {
            return Optional.empty();
        }

        int ambiguity = ambiguityCalculator.computeCombo(
                ambiguityCalculator.computePopulationGiant(ctx.globalRank()),
                ambiguityCalculator.computeDensity(density, byCountry(), countryId)
        );

        return buildComboHint(ctx, "WB_DENSE_GIANT_PROFILE_", "dense-giant",
                Map.of("population", formatPopulation(ctx.statistic().getValue()),
                        "density", formatDecimal(density, 0),
                        "ordinal", ordinal(ctx.globalRank())),
                Category.DEMOGRAPHICS, HintType.RELATIVE_FACT, ambiguity, 20, BigDecimal.valueOf(0.90));
    }

    private Optional<CandidateHint> generateOpenServiceCombo(StatisticContext ctx,
                                                              Map<String, BigDecimal> stats,
                                                              Long countryId) {
        BigDecimal trade = stats.get("NE.TRD.GNFS.ZS");
        BigDecimal services = stats.get("NV.SRV.TOTL.ZS");
        if (trade == null || services == null
                || trade.compareTo(BigDecimal.valueOf(100)) < 0
                || services.compareTo(BigDecimal.valueOf(70)) < 0) {
            return Optional.empty();
        }

        int ambiguity = ambiguityCalculator.computeCombo(
                ambiguityCalculator.computeTrade(trade, byCountry(), countryId),
                ambiguityCalculator.computeService(services, byCountry(), countryId)
        );

        return buildComboHint(ctx, "WB_OPEN_SERVICE_PROFILE_", "open-service",
                Map.of("trade_share", formatPercent(trade),
                        "service_share", formatPercent(services)),
                Category.ECONOMY, HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.88));
    }

    private Optional<CandidateHint> generateGreenConnectedCombo(StatisticContext ctx,
                                                                  Map<String, BigDecimal> stats,
                                                                  Long countryId) {
        BigDecimal renewables = stats.get("EG.FEC.RNEW.ZS");
        BigDecimal internet = stats.get("IT.NET.USER.ZS");
        if (renewables == null || internet == null
                || renewables.compareTo(BigDecimal.valueOf(35)) < 0
                || internet.compareTo(BigDecimal.valueOf(80)) < 0) {
            return Optional.empty();
        }

        int ambiguity = ambiguityCalculator.computeCombo(
                ambiguityCalculator.computeRenewable(renewables, byCountry(), countryId),
                ambiguityCalculator.computeInternet(internet, byCountry(), countryId)
        );

        return buildComboHint(ctx, "WB_GREEN_CONNECTED_", "green-connected",
                Map.of("renewable_share", formatPercent(renewables),
                        "internet_share", formatPercent(internet)),
                Category.ENVIRONMENT, HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.88));
    }

    private Optional<CandidateHint> generateYoungAgriCombo(StatisticContext ctx,
                                                            Map<String, BigDecimal> stats,
                                                            Long countryId) {
        BigDecimal medianAge = stats.get("SP.POP.MED");
        BigDecimal agriculture = stats.get("NV.AGR.TOTL.ZS");
        if (medianAge == null || agriculture == null
                || medianAge.compareTo(BigDecimal.valueOf(22)) > 0
                || agriculture.compareTo(BigDecimal.valueOf(18)) < 0) {
            return Optional.empty();
        }

        int ambiguity = ambiguityCalculator.computeCombo(
                ambiguityCalculator.computeMedianAge(medianAge, byCountry(), countryId),
                ambiguityCalculator.computeAgriculture(agriculture, byCountry(), countryId)
        );

        return buildComboHint(ctx, "WB_YOUNG_AGRI_PROFILE_", "young-agri",
                Map.of("median_age", formatDecimal(medianAge, 1),
                        "agri_share", formatPercent(agriculture)),
                Category.DEMOGRAPHICS, HintType.RELATIVE_FACT, ambiguity, 20, BigDecimal.valueOf(0.89));
    }

    private Optional<CandidateHint> generateConcreteJungleCombo(StatisticContext ctx, Long countryId) {
        Map<String, BigDecimal> stats = numericMapForCountry(countryId);
        BigDecimal urban = stats.get("SP.URB.TOTL.IN.ZS");
        BigDecimal density = stats.get("EN.POP.DNST");
        BigDecimal internet = stats.get("IT.NET.USER.ZS");
        if (urban == null || density == null || internet == null
                || urban.compareTo(BigDecimal.valueOf(85)) < 0
                || density.compareTo(BigDecimal.valueOf(300)) < 0
                || internet.compareTo(BigDecimal.valueOf(90)) < 0) {
            return Optional.empty();
        }

        int ambiguity = ambiguityCalculator.computeCombo(
                ambiguityCalculator.computeUrbanization(urban, byCountry(), countryId),
                ambiguityCalculator.computeDensity(density, byCountry(), countryId),
                ambiguityCalculator.computeInternet(internet, byCountry(), countryId)
        );

        return buildComboHint(ctx, "WB_CONCRETE_JUNGLE_", "concrete-jungle",
                Map.of("urban", formatPercent(urban),
                        "density", formatDecimal(density, 0),
                        "internet", formatPercent(internet)),
                Category.DEMOGRAPHICS, HintType.RELATIVE_FACT, ambiguity, 16, BigDecimal.valueOf(0.90));
    }

    private Optional<CandidateHint> generateDarkRuralCombo(StatisticContext ctx, Long countryId) {
        Map<String, BigDecimal> stats = numericMapForCountry(countryId);
        BigDecimal electricity = stats.get("EG.ELC.ACCS.ZS");
        BigDecimal urban = stats.get("SP.URB.TOTL.IN.ZS");
        BigDecimal agriculture = stats.get("NV.AGR.TOTL.ZS");
        if (electricity == null || urban == null || agriculture == null
                || electricity.compareTo(BigDecimal.valueOf(50)) >= 0
                || urban.compareTo(BigDecimal.valueOf(30)) >= 0
                || agriculture.compareTo(BigDecimal.valueOf(30)) < 0) {
            return Optional.empty();
        }

        int ambiguity = ambiguityCalculator.computeCombo(
                ambiguityCalculator.computeElectricity(electricity, byCountry(), countryId),
                ambiguityCalculator.computeUrbanization(urban, byCountry(), countryId),
                ambiguityCalculator.computeAgriculture(agriculture, byCountry(), countryId)
        );

        return buildComboHint(ctx, "WB_DARK_RURAL_", "dark-rural",
                Map.of("electricity", formatPercent(electricity),
                        "urban", formatPercent(urban),
                        "agri", formatPercent(agriculture)),
                Category.INFRASTRUCTURE, HintType.RELATIVE_FACT, ambiguity, 18, BigDecimal.valueOf(0.88));
    }

    private Optional<CandidateHint> generateBabyBoomCombo(StatisticContext ctx, Long countryId) {
        Map<String, BigDecimal> stats = numericMapForCountry(countryId);
        BigDecimal fertility = stats.get("SP.DYN.TFRT.IN");
        BigDecimal growth = stats.get("SP.POP.GROW");
        BigDecimal medianAge = stats.get("SP.POP.MED");
        if (fertility == null || growth == null || medianAge == null
                || fertility.compareTo(BigDecimal.valueOf(4.5)) < 0
                || growth.compareTo(BigDecimal.valueOf(2.5)) < 0
                || medianAge.compareTo(BigDecimal.valueOf(20)) > 0) {
            return Optional.empty();
        }

        int ambiguity = ambiguityCalculator.computeCombo(
                ambiguityCalculator.computeFertility(fertility, byCountry(), countryId),
                ambiguityCalculator.computeMedianAge(medianAge, byCountry(), countryId)
        );

        return buildComboHint(ctx, "WB_BABY_BOOM_", "baby-boom",
                Map.of("fertility", formatDecimal(fertility, 1),
                        "growth", formatDecimal(growth, 1)),
                Category.DEMOGRAPHICS, HintType.RELATIVE_FACT, ambiguity, 18, BigDecimal.valueOf(0.88));
    }

    private List<CandidateHint> generateYouthBulgeHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(42)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeYouthBulge(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_YOUTH_BULGE_", "youth-bulge",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateElderlyHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(20)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeElderly(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_SILVER_SOCIETY_", "silver-society",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateNetMigrationHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();

        if (value.compareTo(BigDecimal.valueOf(300_000)) >= 0) {
            int ambiguity = ambiguityCalculator.computeNetMigration(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_MIGRATION_MAGNET_", "migration-magnet",
                    Map.of("count", formatPopulation(value)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                    .map(List::of).orElseGet(List::of);
        }

        if (value.compareTo(BigDecimal.valueOf(-300_000)) <= 0) {
            int ambiguity = ambiguityCalculator.computeNetMigration(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_BRAIN_DRAIN_", "brain-drain",
                    Map.of("count", formatPopulation(value.abs())),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                    .map(List::of).orElseGet(List::of);
        }

        return List.of();
    }

    private List<CandidateHint> generateGdpGrowthHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();

        if (value.compareTo(BigDecimal.valueOf(7)) >= 0) {
            int ambiguity = ambiguityCalculator.computeGdpGrowth(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_GDP_BOOM_", "gdp-boom",
                    Map.of("rate", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                    .map(List::of).orElseGet(List::of);
        }

        if (value.compareTo(BigDecimal.valueOf(-3)) <= 0) {
            int ambiguity = ambiguityCalculator.computeGdpGrowth(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_GDP_CRASH_", "gdp-crash",
                    Map.of("rate", formatDecimal(value.abs(), 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                    .map(List::of).orElseGet(List::of);
        }

        return List.of();
    }

    private List<CandidateHint> generateInflationHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(20)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeInflation(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_HYPERINFLATION_", "hyperinflation",
                Map.of("rate", formatDecimal(value, 0)),
                HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.87))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateUnemploymentHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(18)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeUnemployment(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_UNEMPLOYMENT_CRISIS_", "unemployment-crisis",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateDebtHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(100)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeDebt(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_DEBT_HEAVY_", "debt-heavy",
                Map.of("pct", formatDecimal(value, 0)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateGiniHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();

        if (value.compareTo(BigDecimal.valueOf(48)) >= 0) {
            int ambiguity = ambiguityCalculator.computeGini(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_HIGH_INEQUALITY_", "high-inequality",
                    Map.of("value", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                    .map(List::of).orElseGet(List::of);
        }

        if (value.compareTo(BigDecimal.valueOf(27)) <= 0) {
            int ambiguity = ambiguityCalculator.computeGini(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_VERY_EQUAL_", "very-equal",
                    Map.of("value", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                    .map(List::of).orElseGet(List::of);
        }

        return List.of();
    }

    private List<CandidateHint> generateIndustryHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(40)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeIndustry(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_INDUSTRIAL_POWERHOUSE_", "industrial-powerhouse",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateResourceRentsHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(20)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeResourceRents(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_RESOURCE_DEPENDENT_", "resource-dependent",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateTourismGiantHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || !ctx.hasGlobalRank() || ctx.globalRank() > 10
                || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(10_000_000_000L)) < 0) {
            return List.of();
        }

        int ambiguity = ambiguityCalculator.computeTourismGiant(ctx.globalRank());
        return buildSingleHint(ctx, "WB_TOURISM_GIANT_", "tourism-giant",
                Map.of("value", formatUsdCompact(ctx.statistic().getValue()),
                        "ordinal", ordinal(ctx.globalRank())),
                HintType.RANKING, ambiguity, 20, BigDecimal.valueOf(0.88))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateForexReservesHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || !ctx.hasGlobalRank() || ctx.globalRank() > 10
                || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(50_000_000_000L)) < 0) {
            return List.of();
        }

        int ambiguity = ambiguityCalculator.computeForexReserves(ctx.globalRank());
        return buildSingleHint(ctx, "WB_FINANCIAL_FORTRESS_", "financial-fortress",
                Map.of("value", formatUsdCompact(ctx.statistic().getValue()),
                        "ordinal", ordinal(ctx.globalRank())),
                HintType.RANKING, ambiguity, 20, BigDecimal.valueOf(0.88))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateRemittancesHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || !ctx.hasGlobalRank() || ctx.globalRank() > 10
                || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(5_000_000_000L)) < 0) {
            return List.of();
        }

        int ambiguity = ambiguityCalculator.computeRemittances(ctx.globalRank());
        return buildSingleHint(ctx, "WB_REMITTANCE_GIANT_", "remittance-giant",
                Map.of("value", formatUsdCompact(ctx.statistic().getValue()),
                        "ordinal", ordinal(ctx.globalRank())),
                HintType.RANKING, ambiguity, 20, BigDecimal.valueOf(0.87))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateInfantMortalityHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();

        if (value.compareTo(BigDecimal.valueOf(70)) >= 0) {
            int ambiguity = ambiguityCalculator.computeInfantMortality(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_INFANT_MORTALITY_CRISIS_", "infant-mortality-crisis",
                    Map.of("value", formatDecimal(value, 0)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.87))
                    .map(List::of).orElseGet(List::of);
        }

        if (value.compareTo(BigDecimal.valueOf(4)) <= 0) {
            int ambiguity = ambiguityCalculator.computeInfantMortality(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_INFANT_CARE_EXEMPLARY_", "infant-care-exemplary",
                    Map.of("value", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                    .map(List::of).orElseGet(List::of);
        }

        return List.of();
    }

    private List<CandidateHint> generatePhysiciansHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();

        if (value.compareTo(BigDecimal.valueOf(5)) >= 0) {
            int ambiguity = ambiguityCalculator.computePhysicians(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_DOCTOR_RICH_", "doctor-rich",
                    Map.of("value", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                    .map(List::of).orElseGet(List::of);
        }

        if (value.compareTo(BigDecimal.valueOf(0.15)) <= 0) {
            int ambiguity = ambiguityCalculator.computePhysicians(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_DOCTOR_DESERT_", "doctor-desert",
                    Map.of("value", formatDecimal(value, 2)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                    .map(List::of).orElseGet(List::of);
        }

        return List.of();
    }

    private List<CandidateHint> generateAlcoholHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(12)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeAlcohol(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_HEAVY_DRINKING_", "heavy-drinking",
                Map.of("value", formatDecimal(value, 1)),
                HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateHealthSpendingHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(10)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeHealthSpending(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_HEALTHCARE_BIG_SPENDER_", "healthcare-big-spender",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateHivHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(5)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeHiv(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_HIV_CRISIS_", "hiv-crisis",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateLiteracyHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(65)) >= 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeLiteracy(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_LITERACY_GAP_", "literacy-gap",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateEducationSpendingHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(7)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeEducationSpending(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_EDUCATION_PRIORITY_", "education-priority",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateTertiaryEnrollmentHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(80)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeTertiaryEnrollment(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_UNIVERSITY_NATION_", "university-nation",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateAirPollutionHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();

        if (value.compareTo(BigDecimal.valueOf(50)) >= 0) {
            int ambiguity = ambiguityCalculator.computeAirPollution(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_SMOG_CRISIS_", "smog-crisis",
                    Map.of("value", formatDecimal(value, 0)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                    .map(List::of).orElseGet(List::of);
        }

        if (value.compareTo(BigDecimal.valueOf(8)) <= 0) {
            int ambiguity = ambiguityCalculator.computeAirPollution(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_PRISTINE_AIR_", "pristine-air",
                    Map.of("value", formatDecimal(value, 1)),
                    HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                    .map(List::of).orElseGet(List::of);
        }

        return List.of();
    }

    private List<CandidateHint> generateWaterStressHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(80)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeWaterStress(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_WATER_STRESS_", "water-stress",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateRenewableElectricityHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(80)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeRenewableElectricity(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_GREEN_GRID_", "green-grid",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.85))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateBroadbandHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(40)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeBroadband(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_BROADBAND_SATURATED_", "broadband-saturated",
                Map.of("value", formatDecimal(value, 0)),
                HintType.ABSOLUTE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                .map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> generateLandAreaHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();

        if (ctx.hasGlobalRank() && ctx.globalRank() <= 5) {
            int ambiguity = ambiguityCalculator.computeLandAreaRank(ctx.globalRank());
            return buildSingleHint(ctx, "WB_VAST_TERRITORY_", "vast-territory",
                    Map.of("area", formatArea(value),
                            "ordinal", ordinal(ctx.globalRank())),
                    HintType.RANKING, ambiguity, 18, BigDecimal.valueOf(0.89))
                    .map(List::of).orElseGet(List::of);
        }

        if (value.compareTo(BigDecimal.valueOf(1000)) < 0) {
            int ambiguity = ambiguityCalculator.computeLandArea(value, byCountry(), countryId);
            return buildSingleHint(ctx, "WB_TINY_TERRITORY_", "tiny-territory",
                    Map.of("area", formatArea(value)),
                    HintType.ABSOLUTE_FACT, ambiguity, 20, BigDecimal.valueOf(0.86))
                    .map(List::of).orElseGet(List::of);
        }

        return List.of();
    }

    private List<CandidateHint> generateAgriLandHint(StatisticContext ctx) {
        if (!ctx.hasNumericValue() || ctx.statistic().getValue().compareTo(BigDecimal.valueOf(70)) < 0) {
            return List.of();
        }

        BigDecimal value = ctx.statistic().getValue();
        Long countryId = ctx.statistic().getCountryId();
        int ambiguity = ambiguityCalculator.computeAgriLand(value, byCountry(), countryId);

        return buildSingleHint(ctx, "WB_FARMLAND_NATION_", "farmland-nation",
                Map.of("pct", formatPercent(value)),
                HintType.RELATIVE_FACT, ambiguity, 22, BigDecimal.valueOf(0.84))
                .map(List::of).orElseGet(List::of);
    }

    private Optional<CandidateHint> buildSingleHint(StatisticContext ctx,
                                                    String templateBaseCode,
                                                    String salt,
                                                    Map<String, ?> placeholders,
                                                    HintType hintType,
                                                    int ambiguity,
                                                    int defaultDifficulty,
                                                    BigDecimal defaultQuality) {
        String templateCode = templateBaseCode + pickVariant(ctx, salt);
        return buildDynamicTemplatedCandidate(
                difficultyCalculator,
                templateCode,
                placeholders,
                hintType,
                ctx.statistic().getCategory(),
                DataSource.WORLD_BANK,
                ctx.statistic().getYear(),
                ambiguity,
                defaultDifficulty,
                defaultQuality,
                name()
        );
    }

    private Optional<CandidateHint> buildComboHint(StatisticContext ctx,
                                                    String templateBaseCode,
                                                    String salt,
                                                    Map<String, ?> placeholders,
                                                    Category category,
                                                    HintType hintType,
                                                    int ambiguity,
                                                    int defaultDifficulty,
                                                    BigDecimal defaultQuality) {
        String templateCode = templateBaseCode + pickVariant(ctx, salt);
        return buildDynamicTemplatedCandidate(
                difficultyCalculator,
                templateCode,
                placeholders,
                hintType,
                category,
                DataSource.WORLD_BANK,
                ctx.statistic().getYear(),
                ambiguity,
                defaultDifficulty,
                defaultQuality,
                name()
        );
    }

    private void addComboHint(List<CandidateHint> hints, Optional<CandidateHint> candidateHint) {
        candidateHint.ifPresent(hints::add);
    }

    private int pickVariant(StatisticContext ctx, String salt) {
        String seed = (ctx.countryCode() != null ? ctx.countryCode() : "XX") + "|" + salt;
        return Math.floorMod(seed.hashCode(), 3) + 1;
    }

    private String ordinal(int value) {
        if (value % 100 >= 11 && value % 100 <= 13) {
            return value + "th";
        }
        return switch (value % 10) {
            case 1 -> value + "st";
            case 2 -> value + "nd";
            case 3 -> value + "rd";
            default -> value + "th";
        };
    }

    private String formatPopulation(BigDecimal value) {
        double numeric = value.doubleValue();
        if (numeric >= 1_000_000_000d) {
            return formatCompact(numeric / 1_000_000_000d) + " billion";
        }
        if (numeric >= 1_000_000d) {
            return formatCompact(numeric / 1_000_000d) + " million";
        }
        return formatDecimal(value, 0);
    }

    private String formatUsdCompact(BigDecimal value) {
        double numeric = value.doubleValue();
        if (numeric >= 1_000_000_000_000d) {
            return "$" + formatCompact(numeric / 1_000_000_000_000d) + " trillion";
        }
        if (numeric >= 1_000_000_000d) {
            return "$" + formatCompact(numeric / 1_000_000_000d) + " billion";
        }
        if (numeric >= 1_000_000d) {
            return "$" + formatCompact(numeric / 1_000_000d) + " million";
        }
        return "$" + formatDecimal(value, 0);
    }

    private String formatUsdPerPerson(BigDecimal value) {
        double numeric = value.doubleValue();
        if (numeric >= 1_000d) {
            return "$" + formatCompact(numeric / 1_000d) + "k";
        }
        return "$" + formatDecimal(value, 0);
    }

    private String formatPercent(BigDecimal value) {
        return formatDecimal(value, 1) + "%";
    }

    private String formatArea(BigDecimal value) {
        double numeric = value.doubleValue();
        if (numeric >= 1_000_000d) {
            return formatCompact(numeric / 1_000_000d) + " million km²";
        }
        return String.format(Locale.US, "%,.0f", numeric) + " km²";
    }

    private String formatCompact(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.format(Locale.US, "%,.0f", value);
        }
        return String.format(Locale.US, "%,.1f", value);
    }

    private String formatDecimal(BigDecimal value, int decimals) {
        double numeric = value.doubleValue();
        if (decimals <= 0 || Math.abs(numeric - Math.rint(numeric)) < 0.05) {
            return String.format(Locale.US, "%,.0f", numeric);
        }
        return String.format(Locale.US, "%,." + decimals + "f", numeric);
    }
}
