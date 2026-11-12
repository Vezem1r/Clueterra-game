package io.clueterra.budget.source.wikidata;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.clueterra.budget.project.ProjectMetricType;

final class WikidataMetricUnitFormatter {

    private static final int MAX_SOURCE_UNIT_LENGTH = 40;

    private static final Map<String, String> TOKENS_BY_ENTITY_ID = Map.ofEntries(
            Map.entry("Q199", "ONE"),
            Map.entry("Q11573", "M"),
            Map.entry("Q828224", "KM"),
            Map.entry("Q174728", "CM"),
            Map.entry("Q174789", "MM"),
            Map.entry("Q3710", "FT"),
            Map.entry("Q218593", "IN"),
            Map.entry("Q253276", "MI"),
            Map.entry("Q25343", "M2"),
            Map.entry("Q712226", "KM2"),
            Map.entry("Q857027", "FT2"),
            Map.entry("Q25236", "W"),
            Map.entry("Q3320608", "KW"),
            Map.entry("Q6982035", "MW"),
            Map.entry("Q5879479", "GW"),
            Map.entry("Q25517", "M3"),
            Map.entry("Q4243638", "KM3"),
            Map.entry("Q11582", "L"),
            Map.entry("Q11570", "KG"),
            Map.entry("Q41803", "G"),
            Map.entry("Q191118", "T"),
            Map.entry("Q100995", "LB"));

    private static final Map<String, String> TOKENS_BY_LABEL = Map.ofEntries(
            Map.entry("1", "ONE"),
            Map.entry("metre", "M"),
            Map.entry("meter", "M"),
            Map.entry("kilometre", "KM"),
            Map.entry("kilometer", "KM"),
            Map.entry("centimetre", "CM"),
            Map.entry("centimeter", "CM"),
            Map.entry("millimetre", "MM"),
            Map.entry("millimeter", "MM"),
            Map.entry("foot", "FT"),
            Map.entry("inch", "IN"),
            Map.entry("mile", "MI"),
            Map.entry("square metre", "M2"),
            Map.entry("square meter", "M2"),
            Map.entry("square kilometre", "KM2"),
            Map.entry("square kilometer", "KM2"),
            Map.entry("square foot", "FT2"),
            Map.entry("watt", "W"),
            Map.entry("kilowatt", "KW"),
            Map.entry("megawatt", "MW"),
            Map.entry("gigawatt", "GW"),
            Map.entry("cubic metre", "M3"),
            Map.entry("cubic meter", "M3"),
            Map.entry("cubic kilometre", "KM3"),
            Map.entry("cubic kilometer", "KM3"),
            Map.entry("litre", "L"),
            Map.entry("liter", "L"),
            Map.entry("kilogram", "KG"),
            Map.entry("gram", "G"),
            Map.entry("tonne", "T"),
            Map.entry("pound", "LB"));

    private static final Map<String, String> DISPLAY_UNITS = Map.ofEntries(
            Map.entry("M", "m"),
            Map.entry("KM", "km"),
            Map.entry("CM", "cm"),
            Map.entry("MM", "mm"),
            Map.entry("FT", "ft"),
            Map.entry("IN", "in"),
            Map.entry("MI", "mi"),
            Map.entry("M2", "m²"),
            Map.entry("KM2", "km²"),
            Map.entry("FT2", "ft²"),
            Map.entry("W", "W"),
            Map.entry("KW", "kW"),
            Map.entry("MW", "MW"),
            Map.entry("GW", "GW"),
            Map.entry("M3", "m³"),
            Map.entry("KM3", "km³"),
            Map.entry("L", "L"),
            Map.entry("KG", "kg"),
            Map.entry("G", "g"),
            Map.entry("T", "t"),
            Map.entry("LB", "lb"),
            Map.entry("SEAT", "seats"),
            Map.entry("PASSENGERS_PER_YEAR", "passengers/year"));

    private WikidataMetricUnitFormatter() {
    }

    static String sourceUnit(
            ProjectMetricType type,
            String sourceUnitId,
            String sourceUnitLabel,
            String normalizedUnitId) {
        String token = resolvedToken(sourceUnitId, sourceUnitLabel);
        if ("ONE".equals(token)) {
            return type.normalizedUnit();
        }
        if (!token.isBlank()) {
            return token;
        }
        if (Objects.equals(sourceUnitId, normalizedUnitId)) {
            return type.normalizedUnit();
        }
        return safeLabelToken(sourceUnitLabel);
    }

    static String evidenceText(
            ProjectMetricType type,
            BigDecimal sourceValue,
            String sourceUnitId,
            String sourceUnitLabel,
            String normalizedUnitId) {
        String sourceUnit = sourceUnit(type, sourceUnitId, sourceUnitLabel, normalizedUnitId);
        String displayUnit = DISPLAY_UNITS.getOrDefault(sourceUnit, humanLabel(sourceUnitLabel));
        if (displayUnit.isBlank()) {
            displayUnit = sourceUnit;
        }
        return sourceValue.stripTrailingZeros().toPlainString() + " " + displayUnit;
    }

    private static String resolvedToken(String sourceUnitId, String sourceUnitLabel) {
        String entityToken = sourceUnitId == null ? null : TOKENS_BY_ENTITY_ID.get(sourceUnitId);
        if (entityToken != null) {
            return entityToken;
        }
        return TOKENS_BY_LABEL.getOrDefault(humanLabel(sourceUnitLabel), "");
    }

    private static String humanLabel(String sourceUnitLabel) {
        return sourceUnitLabel == null ? "" : sourceUnitLabel.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeLabelToken(String sourceUnitLabel) {
        String token = humanLabel(sourceUnitLabel)
                .replace("²", "2")
                .replace("³", "3")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);
        if (token.isBlank()) {
            return "WIKIDATA_UNIT";
        }
        return token.substring(0, Math.min(token.length(), MAX_SOURCE_UNIT_LENGTH));
    }
}
