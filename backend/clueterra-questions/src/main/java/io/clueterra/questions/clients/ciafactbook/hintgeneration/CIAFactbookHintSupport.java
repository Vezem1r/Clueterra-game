package io.clueterra.questions.clients.ciafactbook.hintgeneration;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class CIAFactbookHintSupport {

    static final int TEXT_MAX = 200;
    static final int NOTE_MAX = 220;
    static final int FLAG_MAX = 220;
    static final int LIST_MAX = 130;
    static final int COMPARATIVE_MAX = 130;
    static final int GOVT_MAX = 100;
    static final int CAPITAL_MAX = 80;
    static final int HISTORY_MAX = 150;
    static final int SYSTEM_MAX = 140;
    static final int RULE_MAX = 140;

    private static final String ELLIPSIS = "...";
    private static final String LIST_SEPARATOR = ", ";
    private static final Pattern LIST_ITEM_SEPARATOR = Pattern.compile("[;,]");
    private static final int INDEPENDENCE_MAX_PARTS = 2;
    private static final int ABBREVIATION_LENGTH = 2;
    private static final int UNKNOWN_BORDER_LENGTH_KM = 0;

    private static final Pattern TRAILING_NUMERIC_PATTERN = Pattern.compile("\\s*[\\d.,]+\\s*(million|thousand|billion)?.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_NUMERIC_PATTERN = Pattern.compile("^[\\d.,]+\\s*(million|thousand|billion)?\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAREN_PATTERN = Pattern.compile("\\([^)]*\\)");
    private static final Pattern BORDER_PATTERN = Pattern.compile("([\\p{L}\\s'.-]+?)\\s+(\\d[\\d,]*)\\s*km", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESCRIPTION_PREFIX = Pattern.compile("^description:\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALL_CAPS_WORD = Pattern.compile("\\b([A-Z]{2,})\\b");
    private static final Pattern ROMAN_NUMERAL_PATTERN = Pattern.compile("^(?i)(I|II|III|IV|V|VI|VII|VIII|IX|X)$");
    private static final Pattern ETYMOLOGY_PREAMBLE = Pattern.compile(
            "^(named after|derives? (its name )?from (the )?|takes its name from( the)?|comes from( the)?|name (derives?|comes) from( the)?|origin of (the )?name[:\\s]+)\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPARATIVE_PREAMBLE = Pattern.compile(
            "^(about|approximately|roughly)\\s+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PERCENT_TRAIL_PATTERN = Pattern.compile(
            "\\s*[<\\d.,]+(?:\\s*-\\s*[<\\d.,]+)?%.*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_ESTIMATE_PATTERN = Pattern.compile("\\s*\\(\\d{4}[^)]*\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern USD_RATE_SUFFIX_PATTERN = Pattern.compile("(?i)\\s*per\\s+US\\s+dollar\\s*-.*$");
    private static final Pattern NOTE_PREFIX_PATTERN = Pattern.compile("(?i)^note\\s*-\\s*");
    private static final Pattern SINCE_PATTERN = Pattern.compile("\\s*\\(since [^)]+\\)", Pattern.CASE_INSENSITIVE);

    private CIAFactbookHintSupport() {
    }

    static String stripTrailingNotes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.replaceAll("\\[[^]]*]", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    static String compactText(String raw, int maxLength) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.replace(';', ',');
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength - ELLIPSIS.length()).trim() + ELLIPSIS;
    }

    static boolean containsInterestingTrigger(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("only")
                || lower.contains("unique")
                || lower.contains("largest")
                || lower.contains("smallest")
                || lower.contains("highest")
                || lower.contains("lowest")
                || lower.contains("longest")
                || lower.contains("world")
                || lower.contains("one of");
    }

    static String extractCompactList(String raw, int limit) {
        return extractList(raw, limit, CIAFactbookHintSupport::cleanListItem, item -> true);
    }

    static String compactSemicolonText(String raw, int maxParts, int maxLength) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String[] parts = raw.split(";");
        String result = Arrays.stream(parts)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(maxParts)
                .collect(Collectors.joining(LIST_SEPARATOR));
        return compactText(result.isEmpty() ? raw.trim() : result, maxLength);
    }

    static String stripEtymologyPreamble(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String stripped = ETYMOLOGY_PREAMBLE.matcher(raw).replaceFirst("");
        if (!StringUtils.hasText(stripped)) {
            return raw;
        }
        return Character.toUpperCase(stripped.charAt(0)) + stripped.substring(1);
    }

    static String stripComparativePreamble(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        return COMPARATIVE_PREAMBLE.matcher(raw).replaceFirst("");
    }

    static String cleanFlagDescription(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String description = DESCRIPTION_PREFIX.matcher(raw).replaceFirst("");
        return compactText(stripTrailingNotes(description), FLAG_MAX);
    }

    static String cleanCapitalCity(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = stripTrailingNotes(raw);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        cleaned = cleaned.replaceFirst(";.*$", "");
        cleaned = cleaned.replaceFirst(",\\s*[^,]*capital.*$", "");
        cleaned = PAREN_PATTERN.matcher(cleaned).replaceAll("").trim();
        return compactText(cleaned, CAPITAL_MAX);
    }

    static String cleanCurrency(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = stripTrailingNotes(raw);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        cleaned = USD_RATE_SUFFIX_PATTERN.matcher(cleaned).replaceFirst("");
        cleaned = cleaned.replaceAll("\\s*-\\s*$", "").trim();
        return compactText(cleaned, CAPITAL_MAX);
    }

    static String cleanIndependence(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = stripTrailingNotes(raw);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        if (cleaned.toLowerCase(Locale.ROOT).startsWith("none")) {
            return compactText(cleaned, HISTORY_MAX);
        }
        return compactSemicolonText(cleaned, INDEPENDENCE_MAX_PARTS, HISTORY_MAX);
    }

    static String cleanLegalSystem(String raw) {
        return compactText(stripTrailingNotes(raw), SYSTEM_MAX);
    }

    static String cleanLeader(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = stripTrailingNotes(raw);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        cleaned = cleaned.replaceFirst(";.*$", "");
        cleaned = SINCE_PATTERN.matcher(cleaned).replaceAll("").trim();
        return compactText(titleCaseAllCaps(cleaned), GOVT_MAX);
    }

    static String cleanVotingAge(String raw) {
        return compactText(stripTrailingNotes(raw), RULE_MAX);
    }

    static String cleanNaturalizationRule(String raw) {
        return cleanRule(raw);
    }

    static String cleanCitizenshipRule(String raw) {
        return cleanRule(raw);
    }

    private static String cleanRule(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = NOTE_PREFIX_PATTERN.matcher(Objects.requireNonNull(stripTrailingNotes(raw))).replaceFirst("");
        String compact = compactText(cleaned, RULE_MAX);
        return compact != null && compact.matches("(?i)^(n/?a|not applicable|unknown|not available|unavailable)$")
                ? null : compact;
    }

    static String extractReligionList(String raw, int limit) {
        return extractList(raw, limit, CIAFactbookHintSupport::cleanReligionItem,
                item -> !isGenericReligionBucket(item));
    }

    static String extractFormerNames(String raw, int limit) {
        return extractList(raw, limit, CIAFactbookHintSupport::cleanSimpleListItem, item -> true);
    }

    static String extractPartnerList(String raw, int limit) {
        return extractList(raw, limit, CIAFactbookHintSupport::cleanPartnerItem, item -> true);
    }

    private static String extractList(String raw, int limit, UnaryOperator<String> cleaner, Predicate<String> keep) {
        String joined = LIST_ITEM_SEPARATOR.splitAsStream(raw)
                .map(String::trim)
                .map(cleaner)
                .filter(StringUtils::hasText)
                .filter(keep)
                .distinct()
                .limit(limit)
                .collect(Collectors.joining(LIST_SEPARATOR));
        return compactText(joined, LIST_MAX);
    }

    static String formatBorderCountryList(List<BorderInfo> borders, int maxCountries, int maxLength) {
        if (borders.isEmpty() || borders.size() > maxCountries) {
            return null;
        }
        String joined = borders.stream()
                .map(BorderInfo::country)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(LIST_SEPARATOR));
        if (!StringUtils.hasText(joined) || joined.length() > maxLength) {
            return null;
        }
        return joined;
    }

    static String titleCaseAllCaps(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        Matcher matcher = ALL_CAPS_WORD.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String word = matcher.group(1);
            String replacement = ROMAN_NUMERAL_PATTERN.matcher(word).matches() || word.length() == ABBREVIATION_LENGTH
                    ? word
                    : word.charAt(0) + word.substring(1).toLowerCase();
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static List<BorderInfo> parseBorders(String raw) {
        List<BorderInfo> borders = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return borders;
        }

        String noParens = PAREN_PATTERN.matcher(raw).replaceAll(" ");
        Matcher matcher = BORDER_PATTERN.matcher(noParens);
        while (matcher.find()) {
            String country = matcher.group(1).trim();
            String distance = matcher.group(2).replace(",", "");
            borders.add(new BorderInfo(cleanBorderCountry(country), Integer.parseInt(distance)));
        }

        if (!borders.isEmpty()) {
            return borders;
        }

        for (String part : raw.split(";")) {
            String cleaned = cleanBorderCountry(part.replaceAll("\\d+\\s*km.*", "").trim());
            if (StringUtils.hasText(cleaned) && !"none".equalsIgnoreCase(cleaned)) {
                borders.add(new BorderInfo(cleaned, UNKNOWN_BORDER_LENGTH_KM));
            }
        }
        return borders;
    }

    static String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    static List<String> sortedBorderCountries(List<BorderInfo> borders) {
        return borders.stream()
                .map(BorderInfo::country)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(value -> value.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static String cleanListItem(String raw) {
        String cleaned = stripTrailingNotes(raw);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        cleaned = PAREN_PATTERN.matcher(cleaned).replaceAll("").trim();
        cleaned = LEADING_NUMERIC_PATTERN.matcher(cleaned).replaceFirst("");
        cleaned = TRAILING_NUMERIC_PATTERN.matcher(cleaned).replaceFirst("").trim();
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private static String cleanSimpleListItem(String raw) {
        String cleaned = stripTrailingNotes(raw);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        cleaned = PAREN_PATTERN.matcher(cleaned).replaceAll("").trim();
        cleaned = YEAR_ESTIMATE_PATTERN.matcher(cleaned).replaceFirst("").trim();
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private static String cleanReligionItem(String raw) {
        return cleanPercentTrailedItem(raw);
    }

    private static String cleanPartnerItem(String raw) {
        return cleanPercentTrailedItem(raw);
    }

    private static String cleanPercentTrailedItem(String raw) {
        String cleaned = cleanSimpleListItem(raw);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        cleaned = PERCENT_TRAIL_PATTERN.matcher(cleaned).replaceFirst("").trim();
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private static boolean isGenericReligionBucket(String raw) {
        String normalized = normalizeKey(raw);
        if (!StringUtils.hasText(normalized)) {
            return true;
        }
        return normalized.equals("other")
                || normalized.equals("none")
                || normalized.equals("no response")
                || normalized.equals("not applicable")
                || normalized.equals("unspecified")
                || normalized.equals("unaffiliated");
    }

    private static String cleanBorderCountry(String raw) {
        return raw.replaceAll("\\(.*\\)", "")
                .replaceAll("^(including|with)\\s+", "")
                .trim();
    }

}
