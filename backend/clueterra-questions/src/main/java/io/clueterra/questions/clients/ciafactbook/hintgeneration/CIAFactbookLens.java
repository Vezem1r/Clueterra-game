package io.clueterra.questions.clients.ciafactbook.hintgeneration;

import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.service.hint.CapitalNameSupport;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CIAFactbookLens implements HintLens {

    private static final int MAX_UNANCHORED_FACT_AMBIGUITY = 10;

    private static final Set<String> OWNED_INDICATORS = Set.of(
            "HIGHEST_POINT", "LOWEST_POINT", "GEOGRAPHY_NOTE", "AREA_COMPARATIVE",
            "BORDER_COUNTRIES", "LAND_BOUNDARIES_TOTAL", "COASTLINE", "CAPITAL_CITY",
            "INDEPENDENCE", "LEGAL_SYSTEM", "CITIZENSHIP_BY_BIRTH", "NATURALIZATION_YEARS",
            "VOTING_AGE", "CHIEF_OF_STATE", "HEAD_OF_GOVERNMENT", "FLAG_DESCRIPTION",
            "NATIONAL_COLORS", "NATIONAL_SYMBOLS", "WORLD_HERITAGE_SITES_COUNT",
            "WORLD_HERITAGE_SITES", "MAJOR_CITIES", "NAME_ETYMOLOGY", "CAPITAL_ETYMOLOGY",
            "CLIMATE", "TERRAIN", "NATURAL_HAZARDS", "GOVERNMENT_TYPE", "NATURAL_RESOURCES",
            "RELIGIONS", "FORMER_NAME", "CURRENCY", "EXPORT_PARTNERS", "IMPORT_PARTNERS",
            "EXPORT_COMMODITIES", "IMPORT_COMMODITIES", "AGRICULTURAL_PRODUCTS", "INDUSTRIES",
            "LOCATION", "ECONOMIC_OVERVIEW"
    );

    private final CIAFactbookAmbiguityCalculator ambiguityCalculator;

    @Override
    public String name() {
        return "cia-factbook";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("CIA_");
    }

    @Override
    public Set<String> claimedIndicators() {
        return OWNED_INDICATORS;
    }

    @Override
    public Set<DataSource> claimedSources() {
        return Set.of(DataSource.CIA_FACTBOOK);
    }

    @Override
    public boolean canApply(StatisticContext ctx) {
        return ctx.statistic().getSource() == DataSource.CIA_FACTBOOK;
    }

    @Override
    public void invalidateCache() {
        ambiguityCalculator.invalidateCache();
    }

    @Override
    public List<CandidateHint> generate(StatisticContext ctx) {
        return switch (ctx.statistic().getIndicatorCode()) {
            case "HIGHEST_POINT" -> generateCleanTextHint(ctx, "CIA_HIGHEST_POINT_", "highest-point", "feature",
                    HintType.ABSOLUTE_FACT, 12, BigDecimal.valueOf(0.90));
            case "LOWEST_POINT" -> generateCleanTextHint(ctx, "CIA_LOWEST_POINT_", "lowest-point", "feature",
                    HintType.ABSOLUTE_FACT, 14, BigDecimal.valueOf(0.84));
            case "GEOGRAPHY_NOTE" -> generateGeographyNoteHints(ctx);
            case "AREA_COMPARATIVE" -> generateAreaComparativeHint(ctx);
            case "BORDER_COUNTRIES" -> generateBorderHints(ctx);
            case "LAND_BOUNDARIES_TOTAL" -> generateBoundaryLengthHints(ctx);
            case "COASTLINE" -> generateCoastlineHints(ctx);
            case "CAPITAL_CITY" -> generateCapitalCityHint(ctx);
            case "INDEPENDENCE" -> generateIndependenceHint(ctx);
            case "LEGAL_SYSTEM" -> generateLegalSystemHint(ctx);
            case "CITIZENSHIP_BY_BIRTH" -> generateCitizenshipByBirthHint(ctx);
            case "NATURALIZATION_YEARS" -> generateNaturalizationYearsHint(ctx);
            case "VOTING_AGE" -> generateVotingAgeHint(ctx);
            case "CHIEF_OF_STATE" -> generateLeaderHint(ctx, "CIA_CHIEF_OF_STATE_", "chief-of-state",
                    18, BigDecimal.valueOf(0.80));
            case "HEAD_OF_GOVERNMENT" -> generateLeaderHint(ctx, "CIA_HEAD_OF_GOVERNMENT_", "head-of-government",
                    16, BigDecimal.valueOf(0.78));
            case "FLAG_DESCRIPTION" -> generateFlagHints(ctx);
            case "NATIONAL_COLORS" -> generateNationalColorsHint(ctx);
            case "NATIONAL_SYMBOLS" -> generateNationalSymbolHints(ctx);
            case "WORLD_HERITAGE_SITES_COUNT" -> generateWorldHeritageCountHints(ctx);
            case "WORLD_HERITAGE_SITES" -> generateListHint(ctx, "CIA_WORLD_HERITAGE_SITES_", "world-heritage",
                    "sites", 2, HintType.CULTURAL, 14, BigDecimal.valueOf(0.90));
            case "MAJOR_CITIES" -> generateMajorCitiesHint(ctx);
            case "NAME_ETYMOLOGY" -> generateCleanTextHint(ctx, "CIA_NAME_ETYMOLOGY_", "name-etymology", "origin",
                    HintType.CULTURAL, 16, BigDecimal.valueOf(0.90));
            case "CAPITAL_ETYMOLOGY" ->
                    generateCleanTextHint(ctx, "CIA_CAPITAL_ETYMOLOGY_", "capital-etymology", "origin",
                            HintType.CULTURAL, 18, BigDecimal.valueOf(0.86));
            case "CLIMATE" -> generateClimateHint(ctx);
            case "TERRAIN" -> generateCleanTextHint(ctx, "CIA_TERRAIN_", "terrain", "description",
                    HintType.ABSOLUTE_FACT, 12, BigDecimal.valueOf(0.90));
            case "NATURAL_HAZARDS" ->
                    generateCleanTextHint(ctx, "CIA_NATURAL_HAZARDS_", "natural-hazards", "description",
                            HintType.ABSOLUTE_FACT, 14, BigDecimal.valueOf(0.88));
            case "GOVERNMENT_TYPE" -> generateGovernmentTypeHint(ctx);
            case "NATURAL_RESOURCES" ->
                    generateListHint(ctx, "CIA_NATURAL_RESOURCES_", "natural-resources", "resources",
                            3, HintType.ABSOLUTE_FACT, 14, BigDecimal.valueOf(0.88));
            case "RELIGIONS" -> generateReligionsHint(ctx);
            case "FORMER_NAME" -> generateFormerNameHint(ctx);
            case "CURRENCY" -> generateCurrencyHint(ctx);
            case "EXPORT_PARTNERS" -> generateExportPartnersHint(ctx);
            case "IMPORT_PARTNERS" -> generateImportPartnersHint(ctx);
            case "EXPORT_COMMODITIES" ->
                    generateListHint(ctx, "CIA_EXPORT_COMMODITIES_", "export-commodities", "commodities",
                            3, HintType.ABSOLUTE_FACT, 14, BigDecimal.valueOf(0.88));
            case "IMPORT_COMMODITIES" -> generateImportCommoditiesHint(ctx);
            case "AGRICULTURAL_PRODUCTS" ->
                    generateListHint(ctx, "CIA_AGRICULTURAL_PRODUCTS_", "agricultural-products", "products",
                            3, HintType.ABSOLUTE_FACT, 14, BigDecimal.valueOf(0.88));
            case "INDUSTRIES" -> generateListHint(ctx, "CIA_INDUSTRIES_", "industries", "industries",
                    3, HintType.ABSOLUTE_FACT, 14, BigDecimal.valueOf(0.88));
            case "LOCATION" -> generateCleanTextHint(ctx, "CIA_LOCATION_", "location", "description",
                    HintType.ABSOLUTE_FACT, 12, BigDecimal.valueOf(0.90));
            case "ECONOMIC_OVERVIEW" -> generateEconomicOverviewHint(ctx);
            default -> List.of();
        };
    }

    private List<CandidateHint> generateGeographyNoteHints(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String cleaned = CIAFactbookHintSupport.compactText(
                CIAFactbookHintSupport.stripTrailingNotes(ctx.statistic().getValueText()), CIAFactbookHintSupport.NOTE_MAX);
        if (!StringUtils.hasText(cleaned) || !CIAFactbookHintSupport.containsInterestingTrigger(cleaned)) {
            return List.of();
        }

        return buildSingleHint(ctx, "CIA_GEOGRAPHY_NOTE_", "geography-note",
                Map.of("note", cleaned), HintType.UNIQUE_FEATURE, 18, BigDecimal.valueOf(0.88));
    }

    private List<CandidateHint> generateAreaComparativeHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String comparison = CIAFactbookHintSupport.stripComparativePreamble(
                CIAFactbookHintSupport.compactText(ctx.statistic().getValueText(), CIAFactbookHintSupport.COMPARATIVE_MAX));
        if (!StringUtils.hasText(comparison)) {
            return List.of();
        }

        return buildSingleHint(ctx, "CIA_AREA_COMPARATIVE_", "area-comparative",
                Map.of("comparison", comparison), HintType.COMPARISON, 12, BigDecimal.valueOf(0.90));
    }

    private List<CandidateHint> generateBorderHints(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String raw = ctx.statistic().getValueText();
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }

        List<BorderInfo> borders = CIAFactbookHintSupport.parseBorders(raw);
        if (borders.isEmpty() && raw.toLowerCase().contains("none")) {
            return List.of();
        }
        if (borders.size() == 1) {
            BorderInfo border = borders.getFirst();
            return buildSingleHint(ctx, "CIA_SOLO_NEIGHBOUR_", "solo-neighbour",
                    Map.of("neighbor", border.country()), HintType.NEIGHBOR, 14, BigDecimal.valueOf(0.92));
        }
        if (borders.size() == 2) {
            return buildSingleHint(ctx, "CIA_TWO_NEIGHBOURS_", "two-neighbours",
                    Map.of("first_neighbor", borders.get(0).country(), "second_neighbor", borders.get(1).country()),
                    HintType.NEIGHBOR, 10, BigDecimal.valueOf(0.95));
        }

        List<CandidateHint> hints = new ArrayList<>();
        String neighborList = CIAFactbookHintSupport.formatBorderCountryList(borders, 6, 90);
        if (StringUtils.hasText(neighborList)) {
            hints.addAll(buildSingleHint(
                    ctx,
                    "CIA_BORDER_COUNTRIES_",
                    "border-countries-list",
                    Map.of("neighbors", neighborList),
                    HintType.NEIGHBOR,
                    14,
                    BigDecimal.valueOf(0.90)
            ));
        }

        if (borders.size() >= 6) {
            hints.addAll(buildSingleHint(
                    ctx,
                    "CIA_NEIGHBOUR_COUNT_HIGH_",
                    "neighbour-count-high",
                    Map.of("count", borders.size()),
                    HintType.NEIGHBOR,
                    18,
                    BigDecimal.valueOf(0.84)
            ));
        }

        borders.stream()
                .filter(border -> border.lengthKm() >= 2000)
                .findFirst()
                .ifPresent(border -> hints.addAll(buildSingleHint(
                        ctx,
                        "CIA_LONG_BORDER_",
                        "long-border",
                        Map.of("neighbor", border.country(), "distance_km", "%,d".formatted(border.lengthKm())),
                        HintType.NEIGHBOR,
                        14,
                        BigDecimal.valueOf(0.90)
                )));

        return hints;
    }

    private List<CandidateHint> generateBoundaryLengthHints(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        int totalLength = ctx.statistic().getValue().intValue();
        if (totalLength == 0) {
            return List.of();
        }
        if (totalLength >= 10000) {
            return buildSingleHint(ctx, "CIA_LONG_FRONTIERS_", "long-frontiers",
                    Map.of("distance_km", "%,d".formatted(totalLength)), HintType.NEIGHBOR, 18, BigDecimal.valueOf(0.82));
        }
        return List.of();
    }

    private List<CandidateHint> generateCoastlineHints(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        int coastline = ctx.statistic().getValue().intValue();
        if (coastline == 0 && ambiguityCalculator.isVerifiedLandlocked(ctx.country().getId())) {
            return buildSingleHint(ctx, "CIA_LANDLOCKED_", "landlocked-coast",
                    Map.of(), HintType.NEIGHBOR, 18, BigDecimal.valueOf(0.78));
        }
        if (coastline >= 20000) {
            return buildSingleHint(ctx, "CIA_COASTLINE_MASSIVE_", "coastline-massive",
                    Map.of("distance_km", "%,d".formatted(coastline)), HintType.NEIGHBOR, 16, BigDecimal.valueOf(0.84));
        }
        return List.of();
    }

    private List<CandidateHint> generateFlagHints(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String description = CIAFactbookHintSupport.cleanFlagDescription(ctx.statistic().getValueText());
        if (!StringUtils.hasText(description) || description.length() < 18) {
            return List.of();
        }

        return buildSingleHint(ctx, "CIA_FLAG_DESCRIPTION_", "flag-description",
                Map.of("description", description), HintType.CULTURAL, 18, BigDecimal.valueOf(0.84));
    }

    private List<CandidateHint> generateCapitalCityHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String capital = CIAFactbookHintSupport.cleanCapitalCity(ctx.statistic().getValueText());
        if (!StringUtils.hasText(capital)) {
            return List.of();
        }
        if (CapitalNameSupport.capitalEchoesCountryName(ctx.country(), capital)) {
            return buildSingleHint(ctx, "CIA_CAPITAL_NAME_MATCH_", "capital-name-match",
                    Map.of(), HintType.ABSOLUTE_FACT, 12, BigDecimal.valueOf(0.86));
        }
        return buildSingleHint(ctx, "CIA_CAPITAL_CITY_", "capital-city",
                Map.of("capital", capital), HintType.ABSOLUTE_FACT, 8, BigDecimal.valueOf(0.88));
    }

    private List<CandidateHint> generateIndependenceHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String independence = CIAFactbookHintSupport.cleanIndependence(ctx.statistic().getValueText());
        if (!StringUtils.hasText(independence)) {
            return List.of();
        }
        return buildSingleHint(ctx, "CIA_INDEPENDENCE_", "independence",
                Map.of("independence", independence), Category.HISTORY,
                HintType.HISTORICAL, 22, BigDecimal.valueOf(0.82));
    }

    private List<CandidateHint> generateLegalSystemHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String system = CIAFactbookHintSupport.cleanLegalSystem(ctx.statistic().getValueText());
        if (!StringUtils.hasText(system)) {
            return List.of();
        }
        return buildSingleHint(ctx, "CIA_LEGAL_SYSTEM_", "legal-system",
                Map.of("system", system), Category.POLITICS,
                HintType.ABSOLUTE_FACT, 18, BigDecimal.valueOf(0.80));
    }

    private List<CandidateHint> generateLeaderHint(StatisticContext ctx,
                                                   String templateBaseCode,
                                                   String salt,
                                                   int defaultDifficulty,
                                                   BigDecimal defaultQuality) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String leader = CIAFactbookHintSupport.cleanLeader(ctx.statistic().getValueText());
        if (!StringUtils.hasText(leader)) {
            return List.of();
        }
        return buildSingleHint(ctx, templateBaseCode, salt,
                Map.of("leader", leader), Category.POLITICS, HintType.ABSOLUTE_FACT, defaultDifficulty, defaultQuality);
    }

    private List<CandidateHint> generateNationalSymbolHints(StatisticContext ctx) {
        return generateListHint(ctx, "CIA_NATIONAL_SYMBOLS_", "national-symbols", "symbols",
                2, HintType.CULTURAL, 4, BigDecimal.valueOf(0.90));
    }

    private List<CandidateHint> generateNationalColorsHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String colors = CIAFactbookHintSupport.extractFormerNames(ctx.statistic().getValueText(), 3);
        if (!StringUtils.hasText(colors)) {
            return List.of();
        }
        return buildSingleHint(ctx, "CIA_NATIONAL_COLORS_", "national-colors",
                Map.of("colors", colors), HintType.CULTURAL, 14, BigDecimal.valueOf(0.86));
    }

    private List<CandidateHint> generateWorldHeritageCountHints(StatisticContext ctx) {
        if (!ctx.hasNumericValue()) {
            return List.of();
        }

        int count = ctx.statistic().getValue().intValue();
        if (count <= 0) {
            return List.of();
        }

        return buildSingleHint(ctx, "CIA_WORLD_HERITAGE_COUNT_", "world-heritage-count",
                Map.of("count", count), HintType.CULTURAL, 16, BigDecimal.valueOf(0.86));
    }

    private List<CandidateHint> generateMajorCitiesHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String raw = ctx.statistic().getValueText();
        String compactList = CIAFactbookHintSupport.extractCompactList(raw, 2);
        if (!StringUtils.hasText(compactList)) {
            return List.of();
        }

        String titleCased = CIAFactbookHintSupport.titleCaseAllCaps(compactList);
        return buildSingleHint(ctx, "CIA_MAJOR_CITIES_", "major-cities",
                Map.of("cities", titleCased), HintType.ABSOLUTE_FACT, 14, BigDecimal.valueOf(0.90));
    }

    private List<CandidateHint> generateGovernmentTypeHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String raw = ctx.statistic().getValueText();
        String cleaned = raw.replaceAll("(?i)\\s*;\\s*note.*$", "").trim();
        cleaned = CIAFactbookHintSupport.compactText(cleaned, CIAFactbookHintSupport.GOVT_MAX);
        if (!StringUtils.hasText(cleaned)
                || ambiguityCalculator.compute(ctx, "government-type") > MAX_UNANCHORED_FACT_AMBIGUITY) {
            return List.of();
        }

        return buildSingleHint(ctx, "CIA_GOVERNMENT_TYPE_", "government-type",
                Map.of("type", cleaned), HintType.ABSOLUTE_FACT, 16, BigDecimal.valueOf(0.82));
    }

    private List<CandidateHint> generateReligionsHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String religions = CIAFactbookHintSupport.extractReligionList(ctx.statistic().getValueText(), 2);
        if (!StringUtils.hasText(religions)) {
            return List.of();
        }
        return buildSingleHint(ctx, "CIA_RELIGIONS_", "religions",
                Map.of("religions", religions), HintType.CULTURAL, 16, BigDecimal.valueOf(0.82));
    }

    private List<CandidateHint> generateFormerNameHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String formerNames = CIAFactbookHintSupport.extractFormerNames(ctx.statistic().getValueText(), 2);
        if (!StringUtils.hasText(formerNames)) {
            return List.of();
        }
        return buildSingleHint(ctx, "CIA_FORMER_NAME_", "former-name",
                Map.of("former_names", formerNames), Category.HISTORY,
                HintType.ABSOLUTE_FACT, 18, BigDecimal.valueOf(0.80));
    }

    private List<CandidateHint> generateCurrencyHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String currency = CIAFactbookHintSupport.cleanCurrency(ctx.statistic().getValueText());
        if (!StringUtils.hasText(currency)) {
            return List.of();
        }
        return buildSingleHint(ctx, "CIA_CURRENCY_", "currency",
                Map.of("currency", currency), HintType.ABSOLUTE_FACT, 12, BigDecimal.valueOf(0.84));
    }

    private List<CandidateHint> generateExportPartnersHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String compactList = CIAFactbookHintSupport.extractCompactList(ctx.statistic().getValueText(), 3);
        if (!StringUtils.hasText(compactList)) {
            return List.of();
        }

        return buildSingleHint(ctx, "CIA_EXPORT_PARTNERS_", "export-partners",
                Map.of("partners", compactList), HintType.ABSOLUTE_FACT, 16, BigDecimal.valueOf(0.86));
    }

    private List<CandidateHint> generateImportPartnersHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String partners = CIAFactbookHintSupport.extractPartnerList(ctx.statistic().getValueText(), 3);
        if (!StringUtils.hasText(partners)) {
            return List.of();
        }

        return buildSingleHint(ctx, "CIA_IMPORT_PARTNERS_", "import-partners",
                Map.of("partners", partners), HintType.ABSOLUTE_FACT, 16, BigDecimal.valueOf(0.84));
    }

    private List<CandidateHint> generateImportCommoditiesHint(StatisticContext ctx) {
        return generateListHint(ctx, "CIA_IMPORT_COMMODITIES_", "import-commodities", "commodities",
                3, HintType.ABSOLUTE_FACT, 14, BigDecimal.valueOf(0.84));
    }

    private List<CandidateHint> generateClimateHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String description = CIAFactbookHintSupport.compactSemicolonText(
                CIAFactbookHintSupport.stripTrailingNotes(ctx.statistic().getValueText()), 1, CIAFactbookHintSupport.TEXT_MAX);
        if (!StringUtils.hasText(description)) {
            return List.of();
        }
        return buildSingleHint(ctx, "CIA_CLIMATE_", "climate",
                Map.of("description", description), HintType.ABSOLUTE_FACT, 14, BigDecimal.valueOf(0.88));
    }

    private List<CandidateHint> generateEconomicOverviewHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String overview = CIAFactbookHintSupport.compactSemicolonText(
                CIAFactbookHintSupport.stripTrailingNotes(ctx.statistic().getValueText()), 3, 200);
        if (!StringUtils.hasText(overview)) {
            return List.of();
        }
        return buildSingleHint(ctx, "CIA_ECONOMIC_OVERVIEW_", "economic-overview",
                Map.of("overview", overview), HintType.ABSOLUTE_FACT, 16, BigDecimal.valueOf(0.86));
    }

    private List<CandidateHint> generateVotingAgeHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String votingAge = CIAFactbookHintSupport.cleanVotingAge(ctx.statistic().getValueText());
        if (!StringUtils.hasText(votingAge)
                || !votingAge.matches(".*\\d.*")
                || ambiguityCalculator.compute(ctx, "voting-age") > MAX_UNANCHORED_FACT_AMBIGUITY) {
            return List.of();
        }
        return buildSingleHint(ctx, "CIA_VOTING_AGE_", "voting-age",
                Map.of("voting_age", votingAge), HintType.ABSOLUTE_FACT, 16, BigDecimal.valueOf(0.76));
    }

    private List<CandidateHint> generateNaturalizationYearsHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String rule = CIAFactbookHintSupport.cleanNaturalizationRule(ctx.statistic().getValueText());
        if (!StringUtils.hasText(rule)) {
            return List.of();
        }
        return buildSingleHint(ctx, "CIA_NATURALIZATION_YEARS_", "naturalization-years",
                Map.of("rule", rule), HintType.ABSOLUTE_FACT, 18, BigDecimal.valueOf(0.78));
    }

    private List<CandidateHint> generateCitizenshipByBirthHint(StatisticContext ctx) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }
        String rule = CIAFactbookHintSupport.cleanCitizenshipRule(ctx.statistic().getValueText());
        if (!StringUtils.hasText(rule)) {
            return List.of();
        }

        String normalized = CIAFactbookHintSupport.normalizeKey(rule);
        if ("yes".equals(normalized)) {
            return buildSingleHint(ctx, "CIA_CITIZENSHIP_BIRTH_YES_", "citizenship-birth-yes",
                    Map.of(), HintType.ABSOLUTE_FACT, 18, BigDecimal.valueOf(0.78));
        }
        if ("no".equals(normalized)) {
            return buildSingleHint(ctx, "CIA_CITIZENSHIP_BIRTH_NO_", "citizenship-birth-no",
                    Map.of(), HintType.ABSOLUTE_FACT, 20, BigDecimal.valueOf(0.80));
        }
        return buildSingleHint(ctx, "CIA_CITIZENSHIP_BIRTH_CONDITIONAL_", "citizenship-birth-conditional",
                Map.of("rule", rule), HintType.ABSOLUTE_FACT, 22, BigDecimal.valueOf(0.82));
    }

    private List<CandidateHint> generateCleanTextHint(StatisticContext ctx,
                                                      String templateBaseCode,
                                                      String salt,
                                                      String placeholder,
                                                      HintType hintType,
                                                      int defaultDifficulty,
                                                      BigDecimal defaultQuality) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String indicatorCode = ctx.statistic().getIndicatorCode();
        String stripped = CIAFactbookHintSupport.stripTrailingNotes(ctx.statistic().getValueText());
        String cleaned = CIAFactbookHintSupport.compactText(stripped, CIAFactbookHintSupport.TEXT_MAX);
        if (!StringUtils.hasText(cleaned)) {
            return List.of();
        }
        if ("NAME_ETYMOLOGY".equals(indicatorCode) || "CAPITAL_ETYMOLOGY".equals(indicatorCode)) {
            cleaned = CIAFactbookHintSupport.stripEtymologyPreamble(cleaned);
        }

        return buildSingleHint(ctx, templateBaseCode, salt,
                Map.of(placeholder, cleaned), hintType, defaultDifficulty, defaultQuality);
    }

    private List<CandidateHint> generateListHint(StatisticContext ctx,
                                                 String templateBaseCode,
                                                 String salt,
                                                 String placeholder,
                                                 int limit,
                                                 HintType hintType,
                                                 int defaultDifficulty,
                                                 BigDecimal defaultQuality) {
        if (!ctx.hasTextValue()) {
            return List.of();
        }

        String compactList = CIAFactbookHintSupport.extractCompactList(ctx.statistic().getValueText(), limit);
        if (!StringUtils.hasText(compactList)) {
            return List.of();
        }

        return buildSingleHint(ctx, templateBaseCode, salt,
                Map.of(placeholder, compactList), hintType, defaultDifficulty, defaultQuality);
    }

    private List<CandidateHint> buildSingleHint(StatisticContext ctx,
                                                String templateBaseCode,
                                                String salt,
                                                Map<String, ?> placeholders,
                                                Category category,
                                                HintType hintType,
                                                int defaultDifficulty,
                                                BigDecimal defaultQuality) {
        String templateCode = templateBaseCode + pickVariant(ctx, salt);
        int ambiguity = ambiguityCalculator.compute(ctx, salt);
        return TemplatedHintBuilder.buildConfiguredStaticTemplatedCandidate(
                ctx,
                templateCode,
                templateCode,
                placeholders,
                category,
                hintType,
                ambiguity,
                defaultDifficulty,
                defaultQuality,
                name(),
                null
        ).map(List::of).orElseGet(List::of);
    }

    private List<CandidateHint> buildSingleHint(StatisticContext ctx,
                                                String templateBaseCode,
                                                String salt,
                                                Map<String, ?> placeholders,
                                                HintType hintType,
                                                int defaultDifficulty,
                                                BigDecimal defaultQuality) {
        String templateCode = templateBaseCode + pickVariant(ctx, salt);
        int ambiguity = ambiguityCalculator.compute(ctx, salt);
        return TemplatedHintBuilder.buildConfiguredStaticTemplatedCandidate(
                ctx,
                templateCode,
                placeholders,
                hintType,
                ambiguity,
                defaultDifficulty,
                defaultQuality,
                name()
        ).map(List::of).orElseGet(List::of);
    }

    private int pickVariant(StatisticContext ctx, String salt) {
        String seed = (ctx.countryCode() != null ? ctx.countryCode() : "XX") + "|" + salt;
        return Math.floorMod(seed.hashCode(), 3) + 1;
    }
}
