package io.clueterra.questions.clients.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.springframework.util.StringUtils;

public final class ImageRightsSupport {

    private static final Pattern CREATIVE_COMMONS_PATTERN =
            Pattern.compile("creativecommons\\.org/licenses/([a-z\\-]+)/([0-9.]+)/?");
    private static final Pattern CREATIVE_COMMONS_ZERO_PATTERN =
            Pattern.compile("creativecommons\\.org/publicdomain/zero/([0-9.]+)/?");
    private static final Pattern PD_MARKER_PATTERN =
            Pattern.compile("(^|\\s)pd([\\s\\-_/]|$)");
    private static final Pattern FILE_TITLE_PREFIX_PATTERN =
            Pattern.compile("^[^\\s:]+\\.(?:jpe?g|png|webp|gif|tiff?|svg)\\s*:\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_WIKI_PREFIX_PATTERN =
            Pattern.compile("^[\\[(]?(?:[a-z]{1,10}\\.)?wiki:\\s*[\"']*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DASHED_USER_SPLIT_PATTERN =
            Pattern.compile("(?i)\\s+-\\s+user\\s*:\\s*");
    private static final Pattern USER_PREFIX_PATTERN =
            Pattern.compile("(?i)\\b(?:[a-z]{1,12}:)?user\\s*:\\s*");
    private static final Pattern DERIVATIVE_WORK_PATTERN =
            Pattern.compile("(?i)\\s+derivative work\\s*:\\s*");
    private static final Pattern BY_PREFIX_PATTERN =
            Pattern.compile("(?i)\\b(?:created|photo(?:graph)?|image|crop|scanned) by\\s+");
    private static final Pattern DONATED_BY_PATTERN =
            Pattern.compile("(?i)\\btaken and donated by\\s+");
    private static final Pattern WT_SHARED_PATTERN =
            Pattern.compile("(?i)\\(wt-shared\\)");
    private static final Pattern TALK_SUFFIX_PATTERN =
            Pattern.compile("(?i)\\s*\\(talk\\)\\s*");
    private static final Pattern WIKIPEDIA_SUFFIX_PATTERN =
            Pattern.compile("(?i)\\s+at\\s+(?:[a-z]+\\s+)?wikipedia\\b");
    private static final Pattern WIKIVOYAGE_SUFFIX_PATTERN =
            Pattern.compile("(?i)\\s+at\\s+wts\\s+wikivoyage\\b");
    private static final Pattern EDGE_NOISE_PATTERN =
            Pattern.compile("^[\\s\"'“”‘’\\[(:;.,-]+|[\\s\"'“”‘’\\])(:;.,-]+$");

    private ImageRightsSupport() {
    }

    public static String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = Jsoup.parse(value).text().replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String cleaned = cleanText(value);
            if (StringUtils.hasText(cleaned)) {
                return cleaned;
            }
        }
        return null;
    }

    public static String licenseLabel(String rawValue) {
        String cleaned = cleanText(rawValue);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);
        Matcher ccZeroMatcher = CREATIVE_COMMONS_ZERO_PATTERN.matcher(lower);
        if (ccZeroMatcher.find()) {
            return "CC0 " + ccZeroMatcher.group(1);
        }
        Matcher creativeCommonsMatcher = CREATIVE_COMMONS_PATTERN.matcher(lower);
        if (creativeCommonsMatcher.find()) {
            return "CC " + creativeCommonsCode(creativeCommonsMatcher.group(1)) + " " + creativeCommonsMatcher.group(2);
        }
        if (lower.contains("public domain") || lower.contains("publicdomain") || PD_MARKER_PATTERN.matcher(lower).find()) {
            return "Public domain";
        }
        return cleaned;
    }

    public static String licenseUrl(String rawValue) {
        String cleaned = cleanText(rawValue);
        return StringUtils.hasText(cleaned)
                && (cleaned.startsWith("http://") || cleaned.startsWith("https://"))
                ? cleaned
                : null;
    }

    public static boolean isCommerciallySafeOpenLicense(String... values) {
        String combined = combinedLower(values);
        if (!StringUtils.hasText(combined)) {
            return false;
        }
        if (containsAny(
                combined,
                "by-nc",
                "by nc",
                "noncommercial",
                "non commercial",
                "by-nd",
                "by nd",
                "no derivatives",
                "no derivative",
                "all rights reserved",
                "fair use",
                "gfdl",
                "gnu free documentation",
                "free documentation license",
                "sampling plus")) {
            return false;
        }
        if (containsAny(
                combined,
                "creativecommons.org/publicdomain/zero/",
                "cc0",
                "public domain",
                "publicdomain",
                "creativecommons.org/licenses/by-sa/",
                "creativecommons.org/licenses/by/",
                "cc by-sa",
                "cc by")) {
            return true;
        }
        return PD_MARKER_PATTERN.matcher(combined).find();
    }

    public static boolean isPublicDomainOrCc0License(String... values) {
        String combined = combinedLower(values);
        if (!StringUtils.hasText(combined)) {
            return false;
        }
        return containsAny(
                combined,
                "creativecommons.org/publicdomain/zero/",
                "creativecommons.org/publicdomain/mark/",
                "cc0",
                "public domain",
                "publicdomain")
                || PD_MARKER_PATTERN.matcher(combined).find();
    }

    public static String wikimediaAuthorName(String rawArtist, String rawCredit) {
        String artist = normalizeWikimediaByline(rawArtist, false);
        if (StringUtils.hasText(artist)) {
            return artist;
        }
        return normalizeWikimediaByline(rawCredit, true);
    }

    public static String wikimediaAttributionText(String authorName, String rawCredit) {
        String byline = StringUtils.hasText(authorName)
                ? authorName
                : normalizeWikimediaByline(rawCredit, true);
        return StringUtils.hasText(byline)
                ? "Image by " + byline + " via Wikimedia Commons."
                : "Image via Wikimedia Commons.";
    }

    private static String combinedLower(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            String cleaned = cleanText(value);
            if (!StringUtils.hasText(cleaned)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(cleaned.toLowerCase(Locale.ROOT));
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeWikimediaByline(String rawValue, boolean allowCreditFallback) {
        String cleaned = cleanText(rawValue);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }

        String normalized = cleaned
                .replace('\u2014', ' ')
                .replace('\u2013', ' ')
                .trim();

        if (!StringUtils.hasText(normalized) || isWikimediaPlaceholder(normalized, allowCreditFallback)) {
            return null;
        }

        normalized = FILE_TITLE_PREFIX_PATTERN.matcher(normalized).replaceFirst("");
        normalized = LEADING_WIKI_PREFIX_PATTERN.matcher(normalized).replaceFirst("");
        normalized = DASHED_USER_SPLIT_PATTERN.matcher(normalized).replaceAll("; ");
        normalized = DERIVATIVE_WORK_PATTERN.matcher(normalized).replaceAll("; ");
        normalized = DONATED_BY_PATTERN.matcher(normalized).replaceAll("");
        normalized = BY_PREFIX_PATTERN.matcher(normalized).replaceAll("");
        normalized = USER_PREFIX_PATTERN.matcher(normalized).replaceAll("");
        normalized = WT_SHARED_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = TALK_SUFFIX_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = WIKIPEDIA_SUFFIX_PATTERN.matcher(normalized).replaceAll("");
        normalized = WIKIVOYAGE_SUFFIX_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.replaceAll("(?i)\\bw:", "");
        normalized = normalized.replaceAll("\\s+", " ").trim();

        List<String> parts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : normalized.split("\\s*;\\s*")) {
            String candidate = trimWikimediaNoise(part);
            if (!StringUtils.hasText(candidate) || isWikimediaPlaceholder(candidate, allowCreditFallback)) {
                continue;
            }
            String key = candidate.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                parts.add(candidate);
            }
        }

        if (parts.isEmpty()) {
            String candidate = trimWikimediaNoise(normalized);
            return StringUtils.hasText(candidate) && !isWikimediaPlaceholder(candidate, allowCreditFallback)
                    ? candidate
                    : null;
        }

        return String.join("; ", parts);
    }

    private static String trimWikimediaNoise(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        String previous;
        do {
            previous = normalized;
            normalized = EDGE_NOISE_PATTERN.matcher(normalized).replaceAll("").trim();
        } while (!normalized.equals(previous));
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private static boolean isWikimediaPlaceholder(String value, boolean allowCreditFallback) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String lower = value.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("—")
                || lower.equals("-")
                || lower.equals("unknown")
                || lower.equals("unknown author")
                || lower.equals("unknown authorunknown author")
                || lower.startsWith("no machine-readable author provided")
                || lower.startsWith("unknown, but since this is a postcard image")) {
            return true;
        }
        return allowCreditFallback && (lower.equals("own work")
                || lower.equals("self-scanned")
                || lower.equals("self photographed")
                || lower.equals("self-photographed")
                || lower.equals("self photograph")
                || lower.equals("own photo")
                || lower.equals("personal work")
                || lower.equals("e-bay")
                || lower.equals("ebay"));
    }

    private static String creativeCommonsCode(String rawCode) {
        if (rawCode == null) {
            return "";
        }
        return switch (rawCode.toLowerCase(Locale.ROOT)) {
            case "by" -> "BY";
            case "by-sa" -> "BY-SA";
            case "by-nc" -> "BY-NC";
            case "by-nc-sa" -> "BY-NC-SA";
            case "by-nd" -> "BY-ND";
            case "by-nc-nd" -> "BY-NC-ND";
            default -> rawCode.toUpperCase(Locale.ROOT).replace('-', ' ');
        };
    }
}
