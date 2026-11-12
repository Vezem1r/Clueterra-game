package io.clueterra.questions.clients.brands.hintgeneration;

import java.time.Clock;
import io.clueterra.questions.clients.brands.dto.BrandConstants;
import io.clueterra.questions.clients.brands.repository.BrandRepository;
import io.clueterra.questions.clients.brands.dto.BrandRow;
import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfig;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.clients.common.AbstractCachedLens;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrandLens extends AbstractCachedLens {

    private static final String TRIGGER_INDICATOR = "BRAND_COUNT";
    private static final BigDecimal DEFAULT_QUALITY = BigDecimal.valueOf(0.70);
    private static final Map<String, String> INDUSTRY_LABELS = Map.ofEntries(
            Map.entry("BRAND_TECH_POWERHOUSE", "technology"),
            Map.entry("BRAND_AUTOMOTIVE_KING", "automotive"),
            Map.entry("BRAND_FASHION_EMPIRE", "fashion"),
            Map.entry("BRAND_LUXURY_CAPITAL", "luxury"),
            Map.entry("BRAND_FOOD_GIANT", "food and drink"),
            Map.entry("BRAND_ENTERTAINMENT_HUB", "entertainment"),
            Map.entry("BRAND_ENERGY_TITAN", "energy"),
            Map.entry("BRAND_FINANCE_CENTER", "finance"),
            Map.entry("BRAND_TRAVEL_KING", "travel"),
            Map.entry("BRAND_STARTUP_NATION", "technology"));

    private final BrandRepository brandRepository;
    private final BrandAmbiguityCalculator ambiguityCalculator;
    private final DynamicDifficultyCalculator difficultyCalculator;
    private final Clock clock;

    private final AtomicReference<Map<String, List<BrandRow>>> cachedAllByCountry = new AtomicReference<>(Map.of());

    @Override
    public String name() {
        return "brand-dynamic";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_DYNAMIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("BRAND_");
    }

    @Override
    public boolean canApply(StatisticContext context) {
        return TRIGGER_INDICATOR.equals(context.statistic().getIndicatorCode());
    }

    @Override
    protected List<CandidateHint> doGenerate(StatisticContext context) {
        String countryCode = context.countryCode();

        Map<String, List<BrandRow>> allByCountry = cachedAllByCountry.get();
        List<BrandRow> rows = allByCountry.getOrDefault(countryCode, List.of()).stream()
                .filter(r -> !BrandConstants.brandContainsCountryName(r.brandName(), countryCode))
                .toList();
        if (rows.isEmpty()) return List.of();

        List<CandidateHint> candidates = new ArrayList<>();

        generateIconicBrandHints(candidates, rows, countryCode, allByCountry);
        generateIndustryHints(candidates, rows, countryCode, allByCountry);
        generateDailyLifeHints(candidates, rows, countryCode, allByCountry);
        generateLegacyHints(candidates, rows, countryCode, allByCountry);

        return candidates;
    }

    private void generateIconicBrandHints(List<CandidateHint> candidates, List<BrandRow> rows,
                                          String countryCode, Map<String, List<BrandRow>> allByCountry) {
        List<BrandRow> topBrands = rows.stream()
                .sorted(Comparator.comparingInt(BrandRow::globalRecognition).reversed())
                .limit(3)
                .toList();

        for (int i = 0; i < topBrands.size(); i++) {
            BrandRow brand = topBrands.get(i);
            addIconicCandidate(candidates, "BRAND_ICONIC_" + (i + 1), countryCode, allByCountry, brand);
        }
    }

    private void addIconicCandidate(List<CandidateHint> candidates, String indicatorCode, String countryCode,
                                    Map<String, List<BrandRow>> allByCountry, BrandRow brand) {
        if (!IndicatorConfig.hasTemplate(indicatorCode)) {
            return;
        }
        int ambiguity = ambiguityCalculator.compute(indicatorCode, countryCode, allByCountry, brand.brandName());
        Integer foundedYear = brand.foundedYear();
        String text = foundedYear != null && foundedYear > 1000 && foundedYear <= LocalDate.now(clock).getYear()
                ? brand.brandName() + " was founded here in " + foundedYear + "."
                : brand.brandName() + " was founded in this country.";
        candidates.add(TemplatedHintBuilder.buildCandidate(
                text, HintType.CULTURAL, Category.CULTURE, DataSource.GLOBAL_BRANDS,
                indicatorCode, LocalDate.now(clock).getYear(),
                difficultyCalculator.computeDifficulty(indicatorCode, ambiguity, 40),
                difficultyCalculator.computeQuality(indicatorCode, ambiguity, DEFAULT_QUALITY),
                ambiguity, name(), null));
    }

    private void generateIndustryHints(List<CandidateHint> candidates, List<BrandRow> rows,
                                       String countryCode, Map<String, List<BrandRow>> allByCountry) {
        Map<String, List<BrandRow>> byIndustry = rows.stream()
                .collect(Collectors.groupingBy(BrandRow::industry));

        tryIndustryHint(candidates, byIndustry, BrandConstants.TECH_INDUSTRIES,
                "BRAND_TECH_POWERHOUSE", countryCode, allByCountry);
        tryIndustryHint(candidates, byIndustry, BrandConstants.AUTOMOTIVE_INDUSTRIES,
                "BRAND_AUTOMOTIVE_KING", countryCode, allByCountry);
        tryIndustryHint(candidates, byIndustry, BrandConstants.FASHION_INDUSTRIES,
                "BRAND_FASHION_EMPIRE", countryCode, allByCountry);
        tryIndustryHint(candidates, byIndustry, BrandConstants.LUXURY_INDUSTRIES,
                "BRAND_LUXURY_CAPITAL", countryCode, allByCountry);
        tryIndustryHint(candidates, byIndustry, BrandConstants.FOOD_INDUSTRIES,
                "BRAND_FOOD_GIANT", countryCode, allByCountry);
        tryIndustryHint(candidates, byIndustry, BrandConstants.ENTERTAINMENT_INDUSTRIES,
                "BRAND_ENTERTAINMENT_HUB", countryCode, allByCountry);
        tryIndustryHint(candidates, byIndustry, BrandConstants.ENERGY_INDUSTRIES,
                "BRAND_ENERGY_TITAN", countryCode, allByCountry);
        tryIndustryHint(candidates, byIndustry, BrandConstants.FINANCE_INDUSTRIES,
                "BRAND_FINANCE_CENTER", countryCode, allByCountry);
        tryIndustryHint(candidates, byIndustry, BrandConstants.TRAVEL_INDUSTRIES,
                "BRAND_TRAVEL_KING", countryCode, allByCountry);
        tryIndustryHint(candidates, byIndustry, BrandConstants.GAMING_INDUSTRIES,
                "BRAND_GAMING_NATION", countryCode, allByCountry);

        List<BrandRow> furnitureBrands = rows.stream()
                .filter(r -> BrandConstants.FURNITURE_INDUSTRIES.contains(r.industry()))
                .sorted(Comparator.comparingInt(BrandRow::globalRecognition).reversed())
                .toList();
        if (!furnitureBrands.isEmpty()) {
            addCandidate(candidates, "BRAND_FURNITURE_MAZE", countryCode, allByCountry,
                    Map.of("brand_1", furnitureBrands.getFirst().brandName()));
        }

        List<BrandRow> fashionBrands = rows.stream()
                .filter(r -> BrandConstants.FASHION_INDUSTRIES.contains(r.industry()))
                .filter(r -> r.globalRecognition() >= BrandConstants.HIGH_RECOGNITION_THRESHOLD)
                .toList();
        if (fashionBrands.size() >= 2) {
            addCandidate(candidates, "BRAND_FAST_FASHION", countryCode, allByCountry,
                    Map.of("brand_1", fashionBrands.getFirst().brandName()));
        }
    }

    private void tryIndustryHint(List<CandidateHint> candidates,
                                 Map<String, List<BrandRow>> byIndustry,
                                 Set<String> industries, String indicatorCode,
                                 String countryCode, Map<String, List<BrandRow>> allByCountry) {
        List<BrandRow> matched = industries.stream()
                .filter(byIndustry::containsKey)
                .flatMap(ind -> byIndustry.get(ind).stream())
                .sorted(Comparator.comparingInt(BrandRow::globalRecognition).reversed())
                .toList();

        if (matched.size() >= BrandConstants.MIN_BRANDS_FOR_INDUSTRY_HINT) {
            addCandidate(candidates, indicatorCode, countryCode, allByCountry,
                    Map.of("brand_1", matched.get(0).brandName(),
                            "brand_2", matched.get(1).brandName()));
        }
    }

    private void generateDailyLifeHints(List<CandidateHint> candidates, List<BrandRow> rows,
                                        String countryCode, Map<String, List<BrandRow>> allByCountry) {
        List<BrandRow> highRecBrands = rows.stream()
                .filter(r -> r.globalRecognition() >= BrandConstants.HIGH_RECOGNITION_THRESHOLD)
                .sorted(Comparator.comparingInt(BrandRow::globalRecognition).reversed())
                .toList();

        if (highRecBrands.size() >= 2) {
            addCandidate(candidates, "BRAND_DAILY_LIFE", countryCode, allByCountry,
                    Map.of("brand_1", highRecBrands.get(0).brandName(),
                            "brand_2", highRecBrands.get(1).brandName()));
        }

        if (rows.size() <= 5 && rows.stream()
                .anyMatch(r -> BrandConstants.TECH_INDUSTRIES.contains(r.industry())
                        && r.globalRecognition() >= 7)) {
            List<BrandRow> techBrands = rows.stream()
                    .filter(r -> BrandConstants.TECH_INDUSTRIES.contains(r.industry()))
                    .sorted(Comparator.comparingInt(BrandRow::globalRecognition).reversed())
                    .toList();
            if (techBrands.size() >= 2) {
                addCandidate(candidates, "BRAND_STARTUP_NATION", countryCode, allByCountry,
                        Map.of("brand_1", techBrands.get(0).brandName(),
                                "brand_2", techBrands.get(1).brandName()));
            }
        }
    }

    private void generateLegacyHints(List<CandidateHint> candidates, List<BrandRow> rows,
                                     String countryCode, Map<String, List<BrandRow>> allByCountry) {
        rows.stream()
                .filter(r -> r.foundedYear() != null && r.foundedYear() > 1000 && r.foundedYear() < 1900)
                .min(Comparator.comparingInt(BrandRow::foundedYear))
                .ifPresent(oldest -> {
                    int age = LocalDate.now(clock).getYear() - oldest.foundedYear();
                    addCandidate(candidates, "BRAND_LEGACY_BRAND", countryCode, allByCountry,
                            Map.of("brand_1", oldest.brandName(),
                                    "founded_year", String.valueOf(oldest.foundedYear()),
                                    "age", String.valueOf(age)));
                });
    }

    @Override
    protected void loadCache() {
        List<BrandRow> allRows = brandRepository.findLastFetched();
        Map<String, List<BrandRow>> byCountry = allRows.stream()
                .collect(Collectors.groupingBy(BrandRow::countryCode));
        cachedAllByCountry.set(Map.copyOf(byCountry));
    }

    private void addCandidate(List<CandidateHint> candidates,
                              String indicatorCode,
                              String countryCode,
                              Map<String, List<BrandRow>> allByCountry,
                              Map<String, String> placeholders) {
        String template = IndicatorConfig.getTemplate(indicatorCode);
        if (!StringUtils.hasText(template)) {
            log.warn("No hint_template found in indicator_config for {}", indicatorCode);
            return;
        }

        String primaryBrand = placeholders.get("brand_1");
        String secondBrand = placeholders.get("brand_2");
        if (secondBrand == null && candidates.stream().anyMatch(candidate -> candidate.text().contains(primaryBrand))) {
            return;
        }
        int ambiguity = ambiguityCalculator.compute(indicatorCode, countryCode, allByCountry, primaryBrand);
        String sector = INDUSTRY_LABELS.get(indicatorCode);
        String text;
        if (secondBrand != null) {
            String subject = sector == null ? "The brands " : "The " + sector + " brands ";
            text = subject + primaryBrand + " and " + secondBrand + " both originated in this country.";
        } else {
            String year = placeholders.get("founded_year");
            text = year != null
                    ? "Founded in " + year + ", " + primaryBrand + " originated in this country."
                    : primaryBrand + " originated in this country.";
        }
        candidates.add(TemplatedHintBuilder.buildCandidate(
                text, HintType.CULTURAL, Category.CULTURE, DataSource.GLOBAL_BRANDS,
                indicatorCode, LocalDate.now(clock).getYear(),
                difficultyCalculator.computeDifficulty(indicatorCode, ambiguity, 40),
                difficultyCalculator.computeQuality(indicatorCode, ambiguity, DEFAULT_QUALITY),
                ambiguity, name(), null));
    }

}
