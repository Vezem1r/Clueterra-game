package io.clueterra.budget.source.wikidata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.clueterra.budget.project.ProjectDatePrecision;
import io.clueterra.budget.project.ProjectMetricType;
import io.clueterra.budget.project.ProjectMilestoneType;

@Component
public class WikipediaInfoboxSupplementExtractor {

    private static final Pattern FIELD_START =
            Pattern.compile("(?i)^\\|\\s*([a-z][a-z0-9_ ]*)\\s*=\\s*(.*)$");
    private static final Pattern REFERENCE_URL =
            Pattern.compile("(?i)(?:\\|\\s*url\\s*=|\\[)\\s*(https?://[^\\s|}\\]<>]+)");
    private static final Pattern NAMED_REFERENCE =
            Pattern.compile("(?i)<ref\\s+name\\s*=\\s*[\"']?([^\"' />]+)[\"']?\\s*/>");
    private static final Pattern CONVERT_VALUE =
            Pattern.compile(
                    "(?i)\\{\\{\\s*(?:convert|cvt)\\s*\\|\\s*([0-9][0-9,.]*)\\s*\\|\\s*([a-z0-9.²³]+)");
    private static final Pattern NUMBER_WITH_UNIT =
            Pattern.compile(
                    "(?i)([0-9][0-9,.]*)\\s*(billion|million|bn|mn)?\\s*(km3|km³|m3|m³|acre["
                            + " .-]?ft|gw|mw|kw|w|km2|km²|m2|m²|sq["
                            + " .-]?ft|ft2|ft²|km|mi|metres?|meters?|m|feet|foot|ft|kg|tonnes?|tons?|lb)?\\b");
    private static final Pattern DATE_TEMPLATE =
            Pattern.compile(
                    "(?i)\\{\\{\\s*(?:start date(?: and age)?|end date(?: and age)?|date)\\s*\\|([^}]*)}}");
    private static final Pattern TEXT_DATE =
            Pattern.compile(
                    "(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2}),?\\s+(\\d{4})\\b");
    private static final Pattern YEAR =
            Pattern.compile("(?<!\\d)(18[5-9]\\d|19\\d{2}|20\\d{2}|21\\d{2})(?!\\d)");
    private static final DateTimeFormatter TEXT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM d uuuu", Locale.ENGLISH);

    public WikipediaInfoboxData extract(
            String wikitext,
            String countryCode,
            String snapshotUrl,
            WikipediaInfoboxCostExtractor costExtractor) {
        Map<String, String> fields = infoboxFields(wikitext);
        List<WikipediaCostMatch> costs = costExtractor.extractAll(wikitext, countryCode, snapshotUrl);
        if (fields.isEmpty()) {
            return new WikipediaInfoboxData(costs, List.of(), List.of());
        }
        List<WikipediaMetricMatch> metrics = new ArrayList<>();
        List<WikipediaMilestoneMatch> milestones = new ArrayList<>();
        fields.forEach(
                (name, value) -> {
                    metricType(name)
                            .flatMap(type -> metric(type, name, value, wikitext, snapshotUrl))
                            .ifPresent(metrics::add);
                    milestoneType(name)
                            .flatMap(type -> milestone(type, name, value, wikitext, snapshotUrl))
                            .ifPresent(milestones::add);
                });
        return new WikipediaInfoboxData(costs, primaryMetrics(metrics), primaryMilestones(milestones));
    }

    private Optional<ProjectMetricType> metricType(String field) {
        return switch (field) {
            case "length",
                 "line_length",
                 "linelength",
                 "track_length",
                 "tracklength",
                 "total_length",
                 "dam_length" -> Optional.of(ProjectMetricType.LENGTH);
            case "mainspan", "main_span" -> Optional.of(ProjectMetricType.MAIN_SPAN);
            case "height", "height_m", "architectural_height", "dam_height" -> Optional.of(ProjectMetricType.HEIGHT);
            case "floor_area", "floorarea", "floor_space" -> Optional.of(ProjectMetricType.FLOOR_AREA);
            case "capacity", "seating_capacity", "stadium_capacity" -> Optional.of(ProjectMetricType.CAPACITY);
            case "plant_capacity",
                 "ps_electrical_capacity",
                 "installed_capacity",
                 "capacity_mw",
                 "power" -> Optional.of(ProjectMetricType.POWER);
            case "annual_passengers", "passengers", "passenger_volume" ->
                    Optional.of(ProjectMetricType.ANNUAL_PASSENGERS);
            case "annual_cargo", "cargo_tonnage", "cargo", "annual_tonnage" ->
                    Optional.of(ProjectMetricType.ANNUAL_CARGO_TONNES);
            case "container_capacity", "container_volume", "annual_teu", "teu" ->
                    Optional.of(ProjectMetricType.CONTAINER_CAPACITY);
            case "res_capacity_total", "storage_capacity", "reservoir_capacity", "volume" ->
                    Optional.of(ProjectMetricType.STORAGE_VOLUME);
            case "launch_mass", "mass" -> Optional.of(ProjectMetricType.MASS);
            default -> Optional.empty();
        };
    }

    private Optional<ProjectMilestoneType> milestoneType(String field) {
        return switch (field) {
            case "construction_began",
                 "construction_start_date",
                 "construction_start",
                 "groundbreaking",
                 "ground_broken",
                 "began",
                 "begin" -> Optional.of(ProjectMilestoneType.CONSTRUCTION_START);
            case "expected_completion",
                 "estimated_completion",
                 "scheduled_completion",
                 "planned_completion",
                 "completion_date_planned" -> Optional.of(ProjectMilestoneType.PLANNED_COMPLETION);
            case "complete", "completed", "completion_date", "construction_end", "built" ->
                    Optional.of(ProjectMilestoneType.ACTUAL_COMPLETION);
            case "opened",
                 "opened_date",
                 "opening_date",
                 "commissioned",
                 "plant_commission",
                 "launch_date" -> Optional.of(ProjectMilestoneType.OPENING);
            default -> Optional.empty();
        };
    }

    private Optional<WikipediaMetricMatch> metric(
            ProjectMetricType type, String field, String value, String wikitext, String snapshotUrl) {
        String searchable = stripMarkup(value);
        Matcher convert = CONVERT_VALUE.matcher(value);
        String number = "";
        String unit = "";
        if (convert.find()) {
            number = convert.group(1);
            unit = convert.group(2);
        } else {
            Matcher plain = NUMBER_WITH_UNIT.matcher(searchable);
            if (type == ProjectMetricType.CAPACITY
                    && searchable.toLowerCase(Locale.ROOT).contains("total")) {
                String total =
                        searchable.substring(searchable.toLowerCase(Locale.ROOT).lastIndexOf("total"));
                plain = NUMBER_WITH_UNIT.matcher(total);
            }
            if (plain.find()) {
                number = plain.group(1);
                String scale = Optional.ofNullable(plain.group(2)).orElse("");
                unit = Optional.ofNullable(plain.group(3)).orElse("");
                number = scaledNumber(number, scale);
            }
        }
        Optional<BigDecimal> sourceValue = decimal(number);
        if (sourceValue.isEmpty()) {
            return Optional.empty();
        }
        String resolvedUnit = unit;
        return normalizeMetric(type, sourceValue.orElseThrow(), resolvedUnit)
                .map(
                        normalized ->
                                new WikipediaMetricMatch(
                                        type,
                                        normalized,
                                        type.normalizedUnit(),
                                        sourceValue.orElseThrow(),
                                        sourceUnit(type, resolvedUnit),
                                        referenceUrl(value, wikitext).orElse(snapshotUrl),
                                        compactEvidence(value),
                                        field));
    }

    private Optional<BigDecimal> normalizeMetric(
            ProjectMetricType type, BigDecimal value, String rawUnit) {
        String unit = normalizedUnitToken(rawUnit);
        BigDecimal factor =
                switch (type) {
                    case LENGTH, MAIN_SPAN, HEIGHT -> lengthFactor(unit);
                    case FLOOR_AREA -> areaFactor(unit);
                    case CAPACITY, ANNUAL_PASSENGERS, ANNUAL_CARGO_TONNES, CONTAINER_CAPACITY ->
                            BigDecimal.ONE;
                    case POWER -> powerFactor(unit);
                    case STORAGE_VOLUME -> volumeFactor(unit);
                    case MASS -> massFactor(unit);
                };
        if (factor == null || value.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(value.multiply(factor).setScale(6, RoundingMode.HALF_UP));
    }

    private BigDecimal lengthFactor(String unit) {
        return switch (unit) {
            case "m", "meter", "meters", "metre", "metres", "" -> BigDecimal.ONE;
            case "km" -> new BigDecimal("1000");
            case "mi" -> new BigDecimal("1609.344");
            case "ft", "foot", "feet" -> new BigDecimal("0.3048");
            default -> null;
        };
    }

    private BigDecimal areaFactor(String unit) {
        return switch (unit) {
            case "m2", "m²", "" -> BigDecimal.ONE;
            case "km2", "km²" -> new BigDecimal("1000000");
            case "sqft", "ft2", "ft²" -> new BigDecimal("0.09290304");
            default -> null;
        };
    }

    private BigDecimal powerFactor(String unit) {
        return switch (unit) {
            case "mw", "" -> BigDecimal.ONE;
            case "gw" -> new BigDecimal("1000");
            case "kw" -> new BigDecimal("0.001");
            case "w" -> new BigDecimal("0.000001");
            default -> null;
        };
    }

    private BigDecimal volumeFactor(String unit) {
        return switch (unit) {
            case "m3", "m³", "" -> BigDecimal.ONE;
            case "km3", "km³" -> new BigDecimal("1000000000");
            case "acref", "acreft" -> new BigDecimal("1233.48184");
            default -> null;
        };
    }

    private BigDecimal massFactor(String unit) {
        return switch (unit) {
            case "kg", "" -> BigDecimal.ONE;
            case "ton", "tons", "tonne", "tonnes" -> new BigDecimal("1000");
            case "lb" -> new BigDecimal("0.45359237");
            default -> null;
        };
    }

    private Optional<WikipediaMilestoneMatch> milestone(
            ProjectMilestoneType type, String field, String value, String wikitext, String snapshotUrl) {
        List<LocalDate> dates = dates(value);
        Comparator<LocalDate> ordering = Comparator.naturalOrder();
        Optional<LocalDate> date =
                type == ProjectMilestoneType.CONSTRUCTION_START
                        ? dates.stream().min(ordering)
                        : dates.stream().max(ordering);
        String referenceUrl = referenceUrl(value, wikitext).orElse(snapshotUrl);
        if (date.isPresent()) {
            return Optional.of(
                    new WikipediaMilestoneMatch(
                            type,
                            date.orElseThrow(),
                            null,
                            ProjectDatePrecision.DAY,
                            referenceUrl,
                            compactEvidence(value),
                            field));
        }
        List<Integer> years = years(value);
        Optional<Integer> year =
                type == ProjectMilestoneType.CONSTRUCTION_START
                        ? years.stream().min(Integer::compareTo)
                        : years.stream().max(Integer::compareTo);
        return year.map(
                resolved ->
                        new WikipediaMilestoneMatch(
                                type,
                                null,
                                resolved,
                                ProjectDatePrecision.YEAR,
                                referenceUrl,
                                compactEvidence(value),
                                field));
    }

    private List<LocalDate> dates(String value) {
        List<LocalDate> result = new ArrayList<>();
        Matcher template = DATE_TEMPLATE.matcher(value);
        while (template.find()) {
            List<Integer> numbers =
                    Pattern.compile("(?<!\\d)(\\d{1,4})(?!\\d)")
                            .matcher(template.group(1))
                            .results()
                            .map(match -> Integer.parseInt(match.group(1)))
                            .toList();
            for (int index = 0; index + 2 < numbers.size(); index++) {
                int year = numbers.get(index);
                if (year >= 1850 && year <= 2200) {
                    try {
                        result.add(LocalDate.of(year, numbers.get(index + 1), numbers.get(index + 2)));
                    } catch (DateTimeException ignored) {
                        // Invalid template values are ignored; a year-level fallback remains available.
                    }
                    break;
                }
            }
        }
        Matcher textual = TEXT_DATE.matcher(stripMarkup(value));
        while (textual.find()) {
            try {
                result.add(
                        LocalDate.parse(
                                textual.group(1) + " " + textual.group(2) + " " + textual.group(3),
                                TEXT_DATE_FORMATTER));
            } catch (DateTimeParseException ignored) {
                // A malformed date is not persisted as evidence.
            }
        }
        return result;
    }

    private List<Integer> years(String value) {
        return YEAR.matcher(stripMarkup(value))
                .results()
                .map(match -> Integer.parseInt(match.group(1)))
                .distinct()
                .toList();
    }

    private List<WikipediaMetricMatch> primaryMetrics(List<WikipediaMetricMatch> metrics) {
        Map<ProjectMetricType, WikipediaMetricMatch> byType = new LinkedHashMap<>();
        metrics.forEach(
                metric ->
                        byType.merge(
                                metric.metricType(),
                                metric,
                                (first, second) ->
                                        second.normalizedValue().compareTo(first.normalizedValue()) > 0
                                                ? second
                                                : first));
        return List.copyOf(byType.values());
    }

    private List<WikipediaMilestoneMatch> primaryMilestones(
            List<WikipediaMilestoneMatch> milestones) {
        Map<ProjectMilestoneType, WikipediaMilestoneMatch> byType = new LinkedHashMap<>();
        milestones.forEach(
                milestone ->
                        byType.merge(
                                milestone.milestoneType(),
                                milestone,
                                this::preferredMilestone));
        return List.copyOf(byType.values());
    }

    private WikipediaMilestoneMatch preferredMilestone(
            WikipediaMilestoneMatch first, WikipediaMilestoneMatch second) {
        if (first.datePrecision() != second.datePrecision()) {
            return second.datePrecision() == ProjectDatePrecision.DAY ? second : first;
        }
        int firstYear =
                first.milestoneDate() == null ? first.milestoneYear() : first.milestoneDate().getYear();
        int secondYear =
                second.milestoneDate() == null ? second.milestoneYear() : second.milestoneDate().getYear();
        if (first.milestoneType() == ProjectMilestoneType.CONSTRUCTION_START) {
            return secondYear < firstYear ? second : first;
        }
        return secondYear > firstYear ? second : first;
    }

    private Map<String, String> infoboxFields(String wikitext) {
        String infobox = WikipediaWikitextParser.firstInfobox(wikitext);
        if (infobox.isBlank()) {
            return Map.of();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        String currentName = null;
        StringBuilder currentValue = null;
        int templateDepth = 0;
        for (String line : infobox.split("\\R")) {
            Matcher start = FIELD_START.matcher(line.trim());
            if (currentValue == null && start.matches()) {
                currentName = normalizeFieldName(start.group(1));
                currentValue = new StringBuilder(start.group(2));
                templateDepth = templateDepth(start.group(2));
            } else if (currentValue != null && templateDepth <= 0 && start.matches()) {
                fields.put(currentName, currentValue.toString().trim());
                currentName = normalizeFieldName(start.group(1));
                currentValue = new StringBuilder(start.group(2));
                templateDepth = templateDepth(start.group(2));
            } else if (currentValue != null) {
                currentValue.append('\n').append(line);
                templateDepth += templateDepth(line);
            }
        }
        if (currentValue != null) {
            fields.put(currentName, currentValue.toString().trim());
        }
        return fields;
    }

    private Optional<String> referenceUrl(String field, String wikitext) {
        Matcher direct = REFERENCE_URL.matcher(field.replace("&amp;", "&"));
        if (direct.find()) {
            return Optional.of(direct.group(1).replaceAll("[.,;]+$", ""));
        }
        Matcher named = NAMED_REFERENCE.matcher(field);
        while (named.find()) {
            Pattern definition =
                    Pattern.compile(
                            "(?is)<ref\\s+name\\s*=\\s*[\"']?"
                                    + Pattern.quote(named.group(1))
                                    + "[\"']?[^>]*>(.*?)</ref>");
            Matcher reference = definition.matcher(wikitext);
            if (reference.find()) {
                Matcher nested = REFERENCE_URL.matcher(reference.group(1).replace("&amp;", "&"));
                if (nested.find()) {
                    return Optional.of(nested.group(1).replaceAll("[.,;]+$", ""));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> decimal(String value) {
        try {
            return Optional.of(new BigDecimal(value.replace(",", "")));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String scaledNumber(String number, String scale) {
        return switch (scale.toLowerCase(Locale.ROOT)) {
            case "billion", "bn" -> decimal(number).map(value -> value.movePointRight(9).toPlainString()).orElse("");
            case "million", "mn" -> decimal(number).map(value -> value.movePointRight(6).toPlainString()).orElse("");
            default -> number;
        };
    }

    private String sourceUnit(ProjectMetricType type, String rawUnit) {
        String normalized = normalizedUnitToken(rawUnit);
        return normalized.isBlank() ? type.normalizedUnit() : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizedUnitToken(String rawUnit) {
        return Optional.ofNullable(rawUnit)
                .orElse("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[ ._-]", "");
    }

    private String stripMarkup(String value) {
        return value
                .replaceAll("(?s)<ref[^>]*>.*?</ref>", " ")
                .replaceAll("(?s)<ref[^>]*/>", " ")
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("\\[\\[[^]|]+\\|([^]]+)]]", "$1")
                .replaceAll("\\[\\[([^]]+)]]", "$1")
                .replaceAll("'{2,}", "")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String compactEvidence(String value) {
        String compact = stripMarkup(value).replaceAll("\\{\\{|}}", "").trim();
        return compact.substring(0, Math.min(compact.length(), 500));
    }

    private String normalizeFieldName(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private int templateDepth(String value) {
        int depth = 0;
        for (int index = 0; index < value.length() - 1; index++) {
            String pair = value.substring(index, index + 2);
            if ("{{".equals(pair)) {
                depth++;
                index++;
            } else if ("}}".equals(pair)) {
                depth--;
                index++;
            }
        }
        return depth;
    }

}
