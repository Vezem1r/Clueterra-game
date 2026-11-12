package io.clueterra.questions.clients.ciafactbook.hintgeneration;

import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CIAFactbookAmbiguityCalculator {

    private final StatisticRepository statisticRepository;

    private final AtomicReference<Map<Long, List<CountryStatistic>>> cachedByCountry =
            new AtomicReference<>(Map.of());

    public int compute(StatisticContext ctx, String hintKey) {
        if (ctx == null || ctx.statistic() == null || !StringUtils.hasText(hintKey)) {
            return 1;
        }

        String indicatorCode = ctx.statistic().getIndicatorCode();
        return switch (indicatorCode) {
            case "CLIMATE" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.compactSemicolonText(
                                    CIAFactbookHintSupport.stripTrailingNotes(ctx.statistic().getValueText()),
                                    1, CIAFactbookHintSupport.TEXT_MAX));
            case "ECONOMIC_OVERVIEW" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.compactSemicolonText(
                                    CIAFactbookHintSupport.stripTrailingNotes(ctx.statistic().getValueText()),
                                    3, 200));
            case "CAPITAL_CITY" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.cleanCapitalCity(ctx.statistic().getValueText()));
            case "INDEPENDENCE" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.cleanIndependence(ctx.statistic().getValueText()));
            case "LEGAL_SYSTEM" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.cleanLegalSystem(ctx.statistic().getValueText()));
            case "CURRENCY" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.cleanCurrency(ctx.statistic().getValueText()));
            case "VOTING_AGE" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.cleanVotingAge(ctx.statistic().getValueText()));
            case "CITIZENSHIP_BY_BIRTH" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.cleanCitizenshipRule(ctx.statistic().getValueText()));
            case "NATURALIZATION_YEARS" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.cleanNaturalizationRule(ctx.statistic().getValueText()));
            case "CHIEF_OF_STATE", "HEAD_OF_GOVERNMENT" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.cleanLeader(ctx.statistic().getValueText()));
            case "HIGHEST_POINT", "LOWEST_POINT", "TERRAIN",
                 "NATURAL_HAZARDS", "LOCATION" ->
                    countExactProcessedText(indicatorCode, processedCleanText(ctx.statistic().getValueText()));
            case "NAME_ETYMOLOGY", "CAPITAL_ETYMOLOGY" ->
                    countExactProcessedText(indicatorCode, processedEtymologyText(ctx.statistic().getValueText()));
            case "GOVERNMENT_TYPE" ->
                    countExactProcessedText(indicatorCode, processedGovtText(ctx.statistic().getValueText()));
            case "GEOGRAPHY_NOTE" ->
                    countExactProcessedText(indicatorCode, processedGeographyNote(ctx.statistic().getValueText()));
            case "AREA_COMPARATIVE" ->
                    countExactProcessedText(indicatorCode, CIAFactbookHintSupport.stripComparativePreamble(
                            CIAFactbookHintSupport.compactText(ctx.statistic().getValueText(), CIAFactbookHintSupport.COMPARATIVE_MAX)));
            case "FLAG_DESCRIPTION" ->
                    countExactProcessedText(indicatorCode, CIAFactbookHintSupport.cleanFlagDescription(ctx.statistic().getValueText()));
            case "RELIGIONS" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.extractReligionList(ctx.statistic().getValueText(), 2));
            case "FORMER_NAME" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.extractFormerNames(ctx.statistic().getValueText(), 2));
            case "NATIONAL_COLORS" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.extractFormerNames(ctx.statistic().getValueText(), 3));
            case "IMPORT_PARTNERS" ->
                    countExactProcessedText(indicatorCode,
                            CIAFactbookHintSupport.extractPartnerList(ctx.statistic().getValueText(), 3));
            case "WORLD_HERITAGE_SITES", "NATIONAL_SYMBOLS" -> countExactList(indicatorCode, ctx.statistic().getValueText(), 2);
            case "MAJOR_CITIES" ->
                    countExactProcessedText(indicatorCode, processedMajorCities(ctx.statistic().getValueText()));
            case "NATURAL_RESOURCES", "EXPORT_PARTNERS", "EXPORT_COMMODITIES", "IMPORT_COMMODITIES",
                 "AGRICULTURAL_PRODUCTS", "INDUSTRIES" ->
                    countExactList(indicatorCode, ctx.statistic().getValueText(), 3);
            case "WORLD_HERITAGE_SITES_COUNT" -> countExactNumeric(indicatorCode, ctx.statistic().getValue());
            case "LAND_BOUNDARIES_TOTAL" -> computeBoundaryLength(ctx.statistic().getValue(), hintKey);
            case "COASTLINE" -> computeCoastline(ctx.statistic().getValue(), hintKey);
            case "BORDER_COUNTRIES" -> computeBorderCountries(ctx.statistic().getValueText(), hintKey);
            default -> 1;
        };
    }

    public void invalidateCache() {
        cachedByCountry.set(Map.of());
    }

    public boolean isVerifiedLandlocked(Long countryId) {
        List<CountryStatistic> stats = snapshotByCountry().getOrDefault(countryId, List.of());
        CountryStatistic coastline = findStat(stats, "COASTLINE");
        CountryStatistic boundaries = findStat(stats, "LAND_BOUNDARIES_TOTAL");
        boolean hasNoCoastline = coastline != null
                && coastline.getValue() != null
                && coastline.getValue().compareTo(BigDecimal.ZERO) == 0;
        boolean hasLandBoundary = boundaries != null
                && boundaries.getValue() != null
                && boundaries.getValue().compareTo(BigDecimal.ZERO) > 0;
        return hasNoCoastline && (hasLandBoundary || !parseBorders(stats).isEmpty());
    }

    private int computeBorderCountries(String raw, String hintKey) {
        List<BorderInfo> borders = CIAFactbookHintSupport.parseBorders(raw);
        return switch (hintKey) {
            case "landlocked-borders" -> countCountriesByPredicate(stats -> parseBorders(stats).isEmpty());
            case "solo-neighbour" -> {
                if (borders.size() != 1) {
                    yield 1;
                }
                String expected = CIAFactbookHintSupport.normalizeKey(borders.getFirst().country());
                yield countCountriesByPredicate(stats -> {
                    List<BorderInfo> parsed = parseBorders(stats);
                    return parsed.size() == 1
                            && expected != null
                            && expected.equals(CIAFactbookHintSupport.normalizeKey(parsed.getFirst().country()));
                });
            }
            case "two-neighbours" -> {
                if (borders.size() != 2) {
                    yield 1;
                }
                List<String> expected = CIAFactbookHintSupport.sortedBorderCountries(borders);
                yield countCountriesByPredicate(stats -> {
                    List<BorderInfo> parsed = parseBorders(stats);
                    return parsed.size() == 2
                            && CIAFactbookHintSupport.sortedBorderCountries(parsed).equals(expected);
                });
            }
            case "border-countries-list" -> {
                if (borders.size() < 3) {
                    yield 1;
                }
                List<String> expected = CIAFactbookHintSupport.sortedBorderCountries(borders);
                yield countCountriesByPredicate(stats -> {
                    List<BorderInfo> parsed = parseBorders(stats);
                    return parsed.size() >= 3
                            && CIAFactbookHintSupport.sortedBorderCountries(parsed).equals(expected);
                });
            }
            case "neighbour-count-high" -> {
                int count = borders.size();
                yield countCountriesByPredicate(stats -> parseBorders(stats).size() == count);
            }
            case "long-border" -> {
                if (borders.isEmpty()) {
                    yield 1;
                }
                BorderInfo longest = borders.stream()
                        .max(Comparator.comparingInt(BorderInfo::lengthKm))
                        .orElseThrow();

                if (longest.lengthKm() <= 0) {
                    yield 1;
                }
                String expectedNeighbor = CIAFactbookHintSupport.normalizeKey(longest.country());
                int expectedLength = longest.lengthKm();
                yield countCountriesByPredicate(stats -> parseBorders(stats).stream()
                        .anyMatch(border -> expectedNeighbor != null
                                && expectedNeighbor.equals(CIAFactbookHintSupport.normalizeKey(border.country()))
                                && withinRelativeBand(border.lengthKm(), expectedLength)));
            }
            default -> 1;
        };
    }

    private int computeBoundaryLength(BigDecimal value, String hintKey) {
        if (value == null) {
            return 1;
        }
        int numeric = value.intValue();
        return switch (hintKey) {
            case "landlocked-boundaries" -> countExactNumeric("LAND_BOUNDARIES_TOTAL", BigDecimal.ZERO);
            case "long-frontiers" -> countNumericWithinBand("LAND_BOUNDARIES_TOTAL", numeric);
            default -> 1;
        };
    }

    private int computeCoastline(BigDecimal value, String hintKey) {
        if (value == null) {
            return 1;
        }
        int numeric = value.intValue();
        return switch (hintKey) {
            case "landlocked-coast" -> countCountriesByPredicate(this::isVerifiedLandlocked);
            case "coastline-massive" -> countNumericWithinBand("COASTLINE", numeric);
            default -> 1;
        };
    }

    private int countExactProcessedText(String indicatorCode, String expected) {
        if (!StringUtils.hasText(expected)) {
            return 1;
        }
        String normalizedExpected = CIAFactbookHintSupport.normalizeKey(expected);
        return countCountriesByPredicate(stats -> {
            CountryStatistic stat = findStat(stats, indicatorCode);
            if (stat == null || !StringUtils.hasText(stat.getValueText())) {
                return false;
            }
            String processed = switch (indicatorCode) {
                case "GEOGRAPHY_NOTE" -> processedGeographyNote(stat.getValueText());
                case "FLAG_DESCRIPTION" -> CIAFactbookHintSupport.cleanFlagDescription(stat.getValueText());
                case "MAJOR_CITIES" -> processedMajorCities(stat.getValueText());
                case "NAME_ETYMOLOGY", "CAPITAL_ETYMOLOGY" -> processedEtymologyText(stat.getValueText());
                case "GOVERNMENT_TYPE" -> processedGovtText(stat.getValueText());
                case "CAPITAL_CITY" -> CIAFactbookHintSupport.cleanCapitalCity(stat.getValueText());
                case "INDEPENDENCE" -> CIAFactbookHintSupport.cleanIndependence(stat.getValueText());
                case "LEGAL_SYSTEM" -> CIAFactbookHintSupport.cleanLegalSystem(stat.getValueText());
                case "CURRENCY" -> CIAFactbookHintSupport.cleanCurrency(stat.getValueText());
                case "CHIEF_OF_STATE", "HEAD_OF_GOVERNMENT" -> CIAFactbookHintSupport.cleanLeader(stat.getValueText());
                case "VOTING_AGE" -> CIAFactbookHintSupport.cleanVotingAge(stat.getValueText());
                case "CITIZENSHIP_BY_BIRTH" -> CIAFactbookHintSupport.cleanCitizenshipRule(stat.getValueText());
                case "NATURALIZATION_YEARS" -> CIAFactbookHintSupport.cleanNaturalizationRule(stat.getValueText());
                case "RELIGIONS" -> CIAFactbookHintSupport.extractReligionList(stat.getValueText(), 2);
                case "FORMER_NAME" -> CIAFactbookHintSupport.extractFormerNames(stat.getValueText(), 2);
                case "NATIONAL_COLORS" -> CIAFactbookHintSupport.extractFormerNames(stat.getValueText(), 3);
                case "IMPORT_PARTNERS" -> CIAFactbookHintSupport.extractPartnerList(stat.getValueText(), 3);
                case "CLIMATE" -> CIAFactbookHintSupport.compactSemicolonText(CIAFactbookHintSupport.stripTrailingNotes(stat.getValueText()), 1, CIAFactbookHintSupport.TEXT_MAX);
                case "ECONOMIC_OVERVIEW" -> CIAFactbookHintSupport.compactSemicolonText(CIAFactbookHintSupport.stripTrailingNotes(stat.getValueText()), 3, 200);
                default -> processedCleanText(stat.getValueText());
            };
            assert normalizedExpected != null;
            return normalizedExpected.equals(CIAFactbookHintSupport.normalizeKey(processed));
        });
    }

    private int countExactList(String indicatorCode, String raw, int limit) {
        String expected = CIAFactbookHintSupport.extractCompactList(raw, limit);
        if (!StringUtils.hasText(expected)) {
            return 1;
        }
        String normalizedExpected = CIAFactbookHintSupport.normalizeKey(expected);
        return countCountriesByPredicate(stats -> {
            CountryStatistic stat = findStat(stats, indicatorCode);
            if (stat == null || !StringUtils.hasText(stat.getValueText())) {
                return false;
            }
            String processed = CIAFactbookHintSupport.extractCompactList(stat.getValueText(), limit);
            assert normalizedExpected != null;
            return normalizedExpected.equals(CIAFactbookHintSupport.normalizeKey(processed));
        });
    }

    private int countExactNumeric(String indicatorCode, BigDecimal expected) {
        if (expected == null) {
            return 1;
        }
        return countCountriesByPredicate(stats -> {
            CountryStatistic stat = findStat(stats, indicatorCode);
            return stat != null && stat.getValue() != null && stat.getValue().compareTo(expected) == 0;
        });
    }

    private int countNumericWithinBand(String indicatorCode, int expected) {
        if (expected <= 0) {
            return 1;
        }
        return countCountriesByPredicate(stats -> {
            CountryStatistic stat = findStat(stats, indicatorCode);
            return stat != null && stat.getValue() != null
                    && withinRelativeBand(stat.getValue().intValue(), expected);
        });
    }

    private int countCountriesByPredicate(Predicate<List<CountryStatistic>> predicate) {
        Map<Long, List<CountryStatistic>> byCountry = snapshotByCountry();
        if (byCountry.isEmpty()) {
            return 1;
        }
        int matches = (int) byCountry.values().stream().filter(predicate).count();
        return Math.max(1, matches);
    }

    private Map<Long, List<CountryStatistic>> snapshotByCountry() {
        Map<Long, List<CountryStatistic>> cached = cachedByCountry.get();
        if (!cached.isEmpty()) {
            return cached;
        }

        Map<Long, List<CountryStatistic>> loaded = statisticRepository.findCurrentBySource(DataSource.CIA_FACTBOOK).stream()
                .collect(Collectors.groupingBy(CountryStatistic::getCountryId));
        loaded.replaceAll((countryId, stats) -> List.copyOf(stats));
        Map<Long, List<CountryStatistic>> immutable = Map.copyOf(loaded);
        cachedByCountry.set(immutable);
        return immutable;
    }

    private CountryStatistic findStat(List<CountryStatistic> stats, String indicatorCode) {
        return stats.stream()
                .filter(stat -> indicatorCode.equals(stat.getIndicatorCode()))
                .findFirst()
                .orElse(null);
    }

    private List<BorderInfo> parseBorders(List<CountryStatistic> stats) {
        CountryStatistic stat = findStat(stats, "BORDER_COUNTRIES");
        if (stat == null || !StringUtils.hasText(stat.getValueText())) {
            return List.of();
        }
        return CIAFactbookHintSupport.parseBorders(stat.getValueText());
    }

    private boolean isVerifiedLandlocked(List<CountryStatistic> stats) {
        CountryStatistic coastline = findStat(stats, "COASTLINE");
        CountryStatistic boundaries = findStat(stats, "LAND_BOUNDARIES_TOTAL");
        boolean hasNoCoastline = coastline != null
                && coastline.getValue() != null
                && coastline.getValue().compareTo(BigDecimal.ZERO) == 0;
        boolean hasLandBoundary = boundaries != null
                && boundaries.getValue() != null
                && boundaries.getValue().compareTo(BigDecimal.ZERO) > 0;
        return hasNoCoastline && (hasLandBoundary || !parseBorders(stats).isEmpty());
    }

    private String processedCleanText(String raw) {
        return CIAFactbookHintSupport.compactText(CIAFactbookHintSupport.stripTrailingNotes(raw), CIAFactbookHintSupport.TEXT_MAX);
    }

    private String processedEtymologyText(String raw) {
        String cleaned = processedCleanText(raw);
        return CIAFactbookHintSupport.stripEtymologyPreamble(cleaned);
    }

    private String processedGovtText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.replaceAll("(?i)\\s*;\\s*note.*$", "").trim();
        return CIAFactbookHintSupport.compactText(cleaned, CIAFactbookHintSupport.GOVT_MAX);
    }

    private String processedGeographyNote(String raw) {
        String cleaned = CIAFactbookHintSupport.compactText(CIAFactbookHintSupport.stripTrailingNotes(raw), 140);
        return CIAFactbookHintSupport.containsInterestingTrigger(cleaned) ? cleaned : null;
    }

    private String processedMajorCities(String raw) {
        String compactList = CIAFactbookHintSupport.extractCompactList(raw, 2);
        return CIAFactbookHintSupport.titleCaseAllCaps(compactList);
    }

    private boolean withinRelativeBand(int actual, int expected) {
        if (actual <= 0 || expected <= 0) {
            return false;
        }
        double tolerance = Math.max(250.0, expected * 0.25);
        return Math.abs(actual - expected) <= tolerance;
    }
}
