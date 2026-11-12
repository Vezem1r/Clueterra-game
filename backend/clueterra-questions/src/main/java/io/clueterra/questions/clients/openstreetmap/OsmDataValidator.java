package io.clueterra.questions.clients.openstreetmap;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class OsmDataValidator {

    private static final double MAX_ELEVATION_METERS = 8849;
    private static final double MIN_ELEVATION_METERS = -450;
    private static final double MAX_WATERFALL_HEIGHT_METERS = 980;
    private static final double FEET_TO_METERS = 0.3048;

    private static final Set<String> INVALID_LIFECYCLE_PREFIXES = Set.of(
            "disused", "abandoned", "ruins", "demolished", "removed", "razed",
            "was", "proposed", "construction", "planned", "historical"
    );

    private static final Set<String> INVALID_LIFECYCLE_VALUES = Set.of(
            "disused", "abandoned", "demolished", "removed", "razed",
            "proposed", "construction", "planned"
    );

    private static final Pattern HEIGHT_PATTERN = Pattern.compile("^\\s*(-?\\d+(?:[.,]\\d+)?)\\s*(m|meters?|ft|feet|')?\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Set<String> INVALID_NAME_KEYWORDS = Set.of(
            "parking", "drive-thru", "unknown", "n/a", "untitled",
            "way", "node", "relation", "track", "test", "fixme",
            "todo", "temporary", "temp", "example"
    );

    private static final Set<String> TEST_INDICATORS = Set.of(
            "test", "testing", "example", "sample", "demo", "dummy",
            "fake", "placeholder", "template"
    );

    public static boolean isActive(Map<String, String> tags) {
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String key = entry.getKey();

            int colonIndex = key.indexOf(':');
            if (colonIndex > 0) {
                String prefix = key.substring(0, colonIndex).toLowerCase();
                if (INVALID_LIFECYCLE_PREFIXES.contains(prefix)) {
                    return false;
                }
            }

            if ("lifecycle".equals(key) || "status".equals(key)) {
                String value = entry.getValue();
                if (StringUtils.isNotBlank(value) && INVALID_LIFECYCLE_VALUES.contains(value.toLowerCase())) {
                    return false;
                }
            }
        }

        String fixme = tags.get("fixme");
        if (StringUtils.isNotBlank(fixme)) {
            String lower = fixme.toLowerCase();
            if (lower.contains("delete") || lower.contains("remove") || lower.contains("wrong")) {
                return false;
            }
        }

        String note = tags.get("note");
        if (StringUtils.isNotBlank(note)) {
            String lower = note.toLowerCase();
            for (String indicator : TEST_INDICATORS) {
                if (lower.contains(indicator)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static Double parseElevation(String raw) {
        Double value = parseMetricValue(raw, MAX_ELEVATION_METERS, false);
        if (value != null && value < MIN_ELEVATION_METERS) {
            return null;
        }
        return value;
    }

    public static Double parseHeight(String raw) {
        return parseMetricValue(raw, MAX_WATERFALL_HEIGHT_METERS, true);
    }

    private static Double parseMetricValue(String raw, double maxLimit, boolean tryFeetIfHuge) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }

        String clean = raw.replace(",", ".").trim();

        if (clean.contains(";")) {
            clean = clean.split(";")[0].trim();
        }
        if (clean.contains("-") && !clean.startsWith("-")) {
            clean = clean.split("-")[0].trim();
        }

        Matcher matcher = HEIGHT_PATTERN.matcher(clean);
        if (!matcher.matches()) {
            return null;
        }

        try {
            double value = Double.parseDouble(matcher.group(1).replace(",", "."));
            String unit = matcher.group(2);

            if (isFeetUnit(unit)) {
                value *= FEET_TO_METERS;
            } else if (tryFeetIfHuge && unit == null && value > maxLimit && (value * FEET_TO_METERS) <= maxLimit) {
                value *= FEET_TO_METERS;
            }

            if (value > maxLimit || value < -500) {
                return null;
            }

            return Math.round(value * 10.0) / 10.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isValidName(String name) {
        if (StringUtils.isBlank(name)) {
            return false;
        }

        String clean = name.trim();

        if (clean.length() < 3 || clean.length() > 150) {
            return false;
        }

        if (StringUtils.isNumeric(clean.replace(" ", "").replace("-", ""))) {
            return false;
        }

        String lower = clean.toLowerCase();

        for (String keyword : INVALID_NAME_KEYWORDS) {
            if (lower.equals(keyword) || lower.startsWith(keyword + " ") || lower.endsWith(" " + keyword)) {
                return false;
            }
        }

        for (String indicator : TEST_INDICATORS) {
            if (lower.startsWith(indicator + " ") || lower.equals(indicator)) {
                return false;
            }
        }

        if (lower.matches("^(node|way|relation)\\s*#?\\d+$")) {
            return false;
        }

        long letterCount = clean.chars().filter(Character::isLetter).count();
        return letterCount >= 2;
    }

    public static String extractPlantName(Map<String, String> tags) {
        String plantName = tags.get("plant:name");
        if (StringUtils.isNotBlank(plantName)) {
            return normalizePlantName(plantName);
        }

        String siteName = tags.get("site:name");
        if (StringUtils.isNotBlank(siteName)) {
            return normalizePlantName(siteName);
        }

        String name = StringUtils.firstNonBlank(tags.get("name:en"), tags.get("name"));
        if (StringUtils.isNotBlank(name)) {
            return normalizePlantName(name);
        }

        String operator = tags.get("operator");
        String ref = tags.get("ref");
        if (StringUtils.isNotBlank(operator) && StringUtils.isNotBlank(ref)) {
            return normalizePlantName(operator + "_" + ref);
        }

        if (StringUtils.isNotBlank(operator)) {
            String city = StringUtils.firstNonBlank(tags.get("addr:city"), tags.get("addr:municipality"), tags.get("addr:place"));
            if (StringUtils.isNotBlank(city)) {
                return normalizePlantName(operator + "_" + city);
            }
            return normalizePlantName(operator);
        }

        String street = tags.get("addr:street");
        String houseNumber = tags.get("addr:housenumber");
        String city = tags.get("addr:city");
        if (StringUtils.isNotBlank(street) && StringUtils.isNotBlank(houseNumber)) {
            String addr = street + "_" + houseNumber;
            if (StringUtils.isNotBlank(city)) {
                addr = city + "_" + addr;
            }
            return normalizePlantName(addr);
        }

        return null;
    }

    private static String normalizePlantName(String name) {
        return StringUtils.stripAccents(name.toLowerCase())
                .replaceAll("\\b(power|plant|station|reactor|unit|generator|block)\\b", "")
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }

    private static boolean isFeetUnit(String unit) {
        return StringUtils.isNotBlank(unit) &&
                (unit.equalsIgnoreCase("ft") || unit.equalsIgnoreCase("feet") || unit.equals("'"));
    }
}
