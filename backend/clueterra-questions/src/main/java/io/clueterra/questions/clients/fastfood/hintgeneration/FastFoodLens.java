package io.clueterra.questions.clients.fastfood.hintgeneration;

import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.clients.fastfood.FastFoodConfigLoader;
import io.clueterra.questions.clients.fastfood.dto.FastFoodConstants;
import io.clueterra.questions.clients.fastfood.dto.FastFoodData;
import io.clueterra.questions.clients.fastfood.dto.CountryFastFood;
import io.clueterra.questions.clients.fastfood.dto.SignatureItem;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.service.hint.DifficultyCalculator;
import io.clueterra.questions.service.hint.HintScoreSupport;
import io.clueterra.questions.service.hint.IndicatorConfig;
import io.clueterra.questions.service.hint.IndicatorTemplateRenderer;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class FastFoodLens implements HintLens {

    private static final int MAX_USEFUL_AMBIGUITY = 20;
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{[a-z_]+}");
    private static final Pattern UNSUPPORTED_MENU_EXCLUSIVITY = Pattern.compile(
            "(?i)\\s+You won't find this on the menu anywhere else in the world\\.?$");
    private static final Set<String> PATTERN_HANDLED_SUFFIXES = Set.of(
            "KFC_CHRISTMAS", "NO_MCDONALDS", "CHAIN_DESERT", "CHAIN_PARADISE",
            "STARBUCKS_DESERT", "MCDONALDS_PIONEER", "MCDONALDS_LATECOMER");

    private final FastFoodConfigLoader configLoader;

    @Override
    public String name() {
        return "fast-food";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("FF_");
    }

    @Override
    public boolean canApply(StatisticContext context) {
        return "FF_CHAINS_PRESENT".equals(context.statistic().getIndicatorCode());
    }

    @Override
    public List<CandidateHint> generate(StatisticContext context) {
        List<CandidateHint> hints = new ArrayList<>();
        String countryCode = context.countryCode();

        configLoader.getCountryData(countryCode).ifPresent(data -> {
            FastFoodData allData = configLoader.getData();

            generateSignatureItemHints(hints, context, data, countryCode, allData);
            generateLocalChampionHint(hints, context, data, countryCode, allData);
            generateCulturalFactHints(hints, context, data, countryCode, allData);
            generateAbsentChainHint(hints, context, data, allData);
            generatePatternHints(hints, context, data, countryCode, allData);
            generateSpecialFactHints(hints, context, data, countryCode, allData);
        });

        return hints;
    }

    private void generateSignatureItemHints(List<CandidateHint> hints, StatisticContext ctx,
                                            CountryFastFood data, String countryCode, FastFoodData allData) {
        List<SignatureItem> items = data.signatureItems();
        for (int i = 0; i < Math.min(items.size(), FastFoodConstants.MAX_SIGNATURE_ITEMS); i++) {
            SignatureItem item = items.get(i);
            String code = "FF_SIGNATURE_ITEM_" + (i + 1);
            String template = IndicatorConfig.getTemplate(code);
            if (template != null) {
                String text = UNSUPPORTED_MENU_EXCLUSIVITY.matcher(resolveTemplate(template, Map.of(
                        "chain", item.chain(),
                        "item", item.item(),
                        "note", item.note()
                ))).replaceFirst("").trim();

                int ambiguity = FastFoodAmbiguityCalculator.compute(code, countryCode, allData);
                addHint(hints, ctx, text, code, ambiguity, 0.80);
            }
        }
    }

    private void generateLocalChampionHint(List<CandidateHint> hints, StatisticContext ctx,
                                           CountryFastFood data, String countryCode, FastFoodData allData) {
        if (data.localChampion() == null) return;
        var champ = data.localChampion();
        String code = "FF_LOCAL_CHAMPION";
        String template = IndicatorConfig.getTemplate(code);

        String text = resolveTemplate(template, Map.of(
                "name", champ.name(),
                "type", champ.type(),
                "note", champ.note()
        ));

        int ambiguity = FastFoodAmbiguityCalculator.compute(code, countryCode, allData);
        addHint(hints, ctx, text, code, ambiguity, 0.82);
    }

    private void generateCulturalFactHints(List<CandidateHint> hints, StatisticContext ctx,
                                           CountryFastFood data, String countryCode, FastFoodData allData) {
        for (int i = 0; i < Math.min(data.culturalFacts().size(), FastFoodConstants.MAX_CULTURAL_FACTS); i++) {
            String code = "FF_CULTURAL_FACT_" + (i + 1);
            String template = IndicatorConfig.getTemplate(code);

            String text = resolveTemplate(template, Map.of("fact", data.culturalFacts().get(i)));

            int ambiguity = FastFoodAmbiguityCalculator.compute(code, countryCode, allData);
            addHint(hints, ctx, text, code, ambiguity, 0.75);
        }
    }

    private void generateAbsentChainHint(List<CandidateHint> hints, StatisticContext ctx,
                                         CountryFastFood data, FastFoodData allData) {
        if (data.absentChains().isEmpty()) return;

        List<String> candidates = data.absentChains().stream()
                .filter(FastFoodConstants.NOTABLE_ABSENT_REASONS::containsKey)
                .toList();
        if (candidates.isEmpty()) {
            candidates = data.absentChains();
        }

        String bestChain = null;
        int bestAmbiguity = Integer.MAX_VALUE;
        for (String chain : candidates) {
            int amb = FastFoodAmbiguityCalculator.computeAbsentChain(chain, allData.countries());
            if (amb < bestAmbiguity) {
                bestAmbiguity = amb;
                bestChain = chain;
            }
        }

        if (bestChain == null || bestAmbiguity > MAX_USEFUL_AMBIGUITY) return;

        String displayName = FastFoodConstants.CHAIN_DISPLAY_NAMES.getOrDefault(bestChain, bestChain);
        String reason = FastFoodConstants.NOTABLE_ABSENT_REASONS.getOrDefault(bestChain, "the chain hasn't expanded here");

        String code = "FF_ABSENT_CHAIN";
        String template = IndicatorConfig.getTemplate(code);
        String text = resolveTemplate(template, Map.of("chain", displayName, "reason", reason));

        addHint(hints, ctx, text, code, bestAmbiguity, 0.72);
    }

    private void generatePatternHints(List<CandidateHint> hints, StatisticContext ctx,
                                      CountryFastFood data, String countryCode, FastFoodData allData) {
        Map<String, ?> chains = data.chains();

        if ("JP".equals(countryCode) && chains.containsKey("kfc")) {
            generateTemplateHint(hints, ctx, "FF_KFC_CHRISTMAS", Map.of("chain", "KFC"), countryCode, allData, 0.90);
        }

        if (!chains.containsKey("mcdonalds")) {
            int ambiguity = FastFoodAmbiguityCalculator.compute("FF_NO_MCDONALDS", countryCode, allData);
            if (ambiguity <= MAX_USEFUL_AMBIGUITY) {
                String reason = data.specialFacts().getOrDefault("NO_MCDONALDS_REASON",
                        "local market conditions never favored the golden arches");
                generateTemplateHint(hints, ctx, "FF_NO_MCDONALDS", Map.of("reason", reason), countryCode, allData, 0.85);
            }
        }

        if (chains.isEmpty()) {
            generateTemplateHint(hints, ctx, "FF_CHAIN_DESERT", Map.of(), countryCode, allData, 0.85);
        }

        long trackedPresent = FastFoodConstants.TRACKED_CHAINS.stream()
                .filter(chains::containsKey)
                .count();
        if (trackedPresent >= FastFoodConstants.TOTAL_TRACKED) {
            generateTemplateHint(hints, ctx, "FF_CHAIN_PARADISE", Map.of(), countryCode, allData, 0.75);
        }

        if (!chains.containsKey("starbucks") && !chains.isEmpty()) {
            int ambiguity = FastFoodAmbiguityCalculator.compute("FF_STARBUCKS_DESERT", countryCode, allData);
            if (ambiguity <= MAX_USEFUL_AMBIGUITY) {
                generateTemplateHint(hints, ctx, "FF_STARBUCKS_DESERT", Map.of(), countryCode, allData, 0.72);
            }
        }

    }

    private void generateSpecialFactHints(List<CandidateHint> hints, StatisticContext ctx,
                                          CountryFastFood data, String countryCode, FastFoodData allData) {
        data.specialFacts().forEach((suffix, factText) -> {
            if (PATTERN_HANDLED_SUFFIXES.contains(suffix)) return;
            String code = "FF_" + suffix;
            if (!IndicatorConfig.hasTemplate(code)) return;

            String template = IndicatorConfig.getTemplate(code);
            String text = resolveSpecialFact(template, factText);

            int ambiguity = FastFoodAmbiguityCalculator.compute(code, countryCode, allData);
            addHint(hints, ctx, text, code, ambiguity, 0.78);
        });
    }

    private void generateTemplateHint(List<CandidateHint> hints, StatisticContext ctx,
                                      String code, Map<String, String> params,
                                      String countryCode, FastFoodData allData, double quality) {
        if (!IndicatorConfig.hasTemplate(code)) return;
        String template = IndicatorConfig.getTemplate(code);
        String text = resolveTemplate(template, params);

        int ambiguity = FastFoodAmbiguityCalculator.compute(code, countryCode, allData);
        addHint(hints, ctx, text, code, ambiguity, quality);
    }

    private void addHint(List<CandidateHint> hints, StatisticContext ctx,
                         String text, String indicatorCode, int ambiguity, double quality) {
        if (hasUnresolvedPlaceholders(text)) {
            log.warn("[FF] Skipping hint with unresolved placeholders: {} -> {}", indicatorCode, text);
            return;
        }

        int baseDifficulty = IndicatorConfig.getBaseDifficulty(indicatorCode, 35);
        int adjustedDifficulty = adjustForAmbiguity(baseDifficulty, ambiguity);
        BigDecimal adjustedQuality = adjustQualityForAmbiguity(quality, ambiguity);

        hints.add(TemplatedHintBuilder.buildCandidate(
                text,
                HintType.CULTURAL,
                ctx.statistic().getCategory(),
                DataSource.FAST_FOOD,
                indicatorCode,
                ctx.statistic().getYear(),
                DifficultyCalculator.calculate(ctx, adjustedDifficulty),
                adjustedQuality,
                ambiguity,
                name(),
                null
        ));
    }

    private static boolean hasUnresolvedPlaceholders(String text) {
        return text == null || text.isBlank() || UNRESOLVED_PLACEHOLDER.matcher(text).find();
    }

    private static String resolveTemplate(String template, Map<String, String> params) {
        return IndicatorTemplateRenderer.render(template, params);
    }

    private static String resolveSpecialFact(String template, String factText) {
        if (template == null) return factText;
        if (factText.contains("|")) {
            Map<String, String> params = new LinkedHashMap<>();
            for (String part : factText.split("\\|")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    params.put(kv[0].trim(), kv[1].trim());
                }
            }
            return IndicatorTemplateRenderer.render(template, params);
        }
        if (factText.contains("=")) {
            String[] kv = factText.split("=", 2);
            return IndicatorTemplateRenderer.render(template, Map.of(kv[0].trim(), kv[1].trim()));
        }
        return IndicatorTemplateRenderer.render(template, Map.of("fact", factText));
    }

    private static int adjustForAmbiguity(int baseDifficulty, int ambiguity) {
        return HintScoreSupport.applyLogAmbiguityBoost(baseDifficulty, ambiguity, 4.0, 95);
    }

    private static BigDecimal adjustQualityForAmbiguity(double baseQuality, int ambiguity) {
        return HintScoreSupport.applyLogUniquenessPenalty(baseQuality, ambiguity, 0.03, 0.40);
    }
}
