package io.clueterra.questions.clients.fastfood;

import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.fastfood.dto.ChainInfo;
import io.clueterra.questions.clients.fastfood.dto.CountryFastFood;
import io.clueterra.questions.clients.fastfood.dto.SignatureItem;
import io.clueterra.questions.clients.fastfood.dto.FastFoodConstants;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FastFoodEnricher {

    private static final int DATA_REFERENCE_YEAR = 2025;

    private final FastFoodConfigLoader configLoader;

    private static final BigDecimal HIGH_CONFIDENCE = BigDecimal.valueOf(0.85);
    private static final BigDecimal MEDIUM_CONFIDENCE = BigDecimal.valueOf(0.75);
    private static final BigDecimal HIGH_WEIGHT = BigDecimal.valueOf(0.70);
    private static final BigDecimal MEDIUM_WEIGHT = BigDecimal.valueOf(0.60);

    public List<RawCountryData> enrichAll(List<Country> countries) {
        List<RawCountryData> result = new ArrayList<>();
        for (Country country : countries) {
            enrichCountry(country).ifPresent(result::add);
        }
        log.info("[FAST_FOOD] Enriched {} countries with fast food data", result.size());
        return result;
    }

    public Optional<RawCountryData> enrichCountry(Country country) {
        return configLoader.getCountryData(country.getIsoAlpha2())
                .map(data -> buildCountryData(country, data));
    }

    private RawCountryData buildCountryData(Country country, CountryFastFood data) {
        List<RawStatistic> stats = new ArrayList<>();
        int referenceYear = DATA_REFERENCE_YEAR;

        addNumericStats(stats, data, referenceYear);
        addSignatureItemStats(stats, data, referenceYear);
        addLocalChampionStat(stats, data, referenceYear);
        addCulturalFactStats(stats, data, referenceYear);
        addAbsentChainStat(stats, data, referenceYear);
        addSpecialFactStats(stats, data, referenceYear);

        if (stats.isEmpty()) return null;

        return RawCountryData.builder()
                .isoAlpha2(country.getIsoAlpha2())
                .nameEn(country.getNameEn())
                .source(DataSource.FAST_FOOD)
                .statistics(stats)
                .build();
    }

    private void addNumericStats(List<RawStatistic> stats, CountryFastFood data, int year) {
        Map<String, ChainInfo> chains = data.chains();
        if (chains.isEmpty()) return;

        int chainsPresent = (int) FastFoodConstants.TRACKED_CHAINS.stream()
                .filter(chains::containsKey)
                .filter(k -> chains.get(k).locations() > 0)
                .count();

        int totalOutlets = chains.values().stream()
                .mapToInt(ChainInfo::locations)
                .sum();

        int firstArrivalYear = chains.values().stream()
                .mapToInt(ChainInfo::firstYear)
                .filter(y -> y > 0)
                .min()
                .orElse(0);

        int diversityScore = Math.round((float) chainsPresent / FastFoodConstants.TOTAL_TRACKED * 100);

        int uniqueItems = data.signatureItems() != null ? data.signatureItems().size() : 0;

        int localizationScore = computeLocalizationScore(data);

        stats.add(numericStat("FF_CHAINS_PRESENT", "Fast Food Chains Present", chainsPresent, "count", year));
        stats.add(numericStat("FF_TOTAL_OUTLETS", "Total Fast Food Outlets", totalOutlets, "count", year));
        stats.add(numericStat("FF_DIVERSITY_SCORE", "Fast Food Diversity Score", diversityScore, "score", year));
        stats.add(numericStat("FF_UNIQUE_ITEMS_COUNT", "Regional Menu Exclusives", uniqueItems, "count", year));
        stats.add(numericStat("FF_LOCALIZATION_SCORE", "Menu Localization Score", localizationScore, "score", year));

        if (firstArrivalYear > 0) {
            stats.add(numericStat("FF_FIRST_ARRIVAL_YEAR", "First Major Chain Arrival", firstArrivalYear, "year", year));
        }

        FastFoodConstants.CHAIN_TO_INDICATOR.forEach((chainKey, indicatorCode) -> {
            ChainInfo info = chains.get(chainKey);
            if (info != null && info.locations() > 0) {
                String displayName = FastFoodConstants.CHAIN_DISPLAY_NAMES.get(chainKey) + " Locations";
                stats.add(numericStat(indicatorCode, displayName, info.locations(), "count", year));
            }
        });

        if (totalOutlets > 0) {
            stats.add(numericStat("FF_DENSITY_PER_MILLION", "Fast Food Density per Million", totalOutlets, "per million", year));
        }
    }

    private void addSignatureItemStats(List<RawStatistic> stats, CountryFastFood data, int year) {
        List<SignatureItem> items = data.signatureItems();
        if (items == null) return;

        for (int i = 0; i < Math.min(items.size(), FastFoodConstants.MAX_SIGNATURE_ITEMS); i++) {
            SignatureItem item = items.get(i);
            String code = "FF_SIGNATURE_ITEM_" + (i + 1);
            String valueText = item.chain() + "|" + item.item() + "|" + item.note();
            stats.add(textStat(code, "Signature Menu Item", valueText, year, HIGH_WEIGHT));
        }
    }

    private void addLocalChampionStat(List<RawStatistic> stats, CountryFastFood data, int year) {
        if (data.localChampion() == null) return;
        var champ = data.localChampion();
        String valueText = champ.name() + "|" + champ.type() + "|" + champ.note();
        stats.add(textStat("FF_LOCAL_CHAMPION", "Local Fast Food Champion", valueText, year, HIGH_WEIGHT));
    }

    private void addCulturalFactStats(List<RawStatistic> stats, CountryFastFood data, int year) {
        List<String> facts = data.culturalFacts();
        if (facts == null) return;

        for (int i = 0; i < Math.min(facts.size(), FastFoodConstants.MAX_CULTURAL_FACTS); i++) {
            String code = "FF_CULTURAL_FACT_" + (i + 1);
            stats.add(textStat(code, "Fast Food Cultural Fact", facts.get(i), year, MEDIUM_WEIGHT));
        }
    }

    private void addAbsentChainStat(List<RawStatistic> stats, CountryFastFood data, int year) {
        List<String> absent = data.absentChains();
        if (absent == null || absent.isEmpty()) return;

        String mostNotable = absent.stream()
                .filter(FastFoodConstants.NOTABLE_ABSENT_REASONS::containsKey)
                .findFirst()
                .orElse(absent.getFirst());

        String displayName = FastFoodConstants.CHAIN_DISPLAY_NAMES.getOrDefault(mostNotable, mostNotable);
        String reason = FastFoodConstants.NOTABLE_ABSENT_REASONS.getOrDefault(mostNotable, "the chain hasn't expanded here");
        String valueText = displayName + "|" + reason;
        stats.add(textStat("FF_ABSENT_CHAIN", "Notable Absent Chain", valueText, year, MEDIUM_WEIGHT));
    }

    private void addSpecialFactStats(List<RawStatistic> stats, CountryFastFood data, int year) {
        if (data.specialFacts() == null) return;

        data.specialFacts().forEach((suffix, factText) -> {
            String code = "FF_" + suffix;
            stats.add(textStat(code, "Fast Food Special Fact", factText, year, HIGH_WEIGHT));
        });
    }

    private int computeLocalizationScore(CountryFastFood data) {
        int score = 0;
        if (data.signatureItems() != null) score += Math.min(data.signatureItems().size() * 15, 40);
        if (data.localChampion() != null) score += 20;
        if (data.specialFacts() != null) score += Math.min(data.specialFacts().size() * 10, 30);
        if (data.culturalFacts() != null && !data.culturalFacts().isEmpty()) score += 10;
        return Math.min(score, 100);
    }

    private RawStatistic numericStat(String code, String name, int value, String unit, int year) {
        return new RawStatistic(code, name, Category.CULTURE,
                BigDecimal.valueOf(value), null, unit, year,
                HIGH_CONFIDENCE, HIGH_WEIGHT);
    }

    private RawStatistic textStat(String code, String name, String valueText, int year, BigDecimal weight) {
        return new RawStatistic(code, name, Category.CULTURE,
                null, valueText, null, year,
                MEDIUM_CONFIDENCE, weight);
    }
}
