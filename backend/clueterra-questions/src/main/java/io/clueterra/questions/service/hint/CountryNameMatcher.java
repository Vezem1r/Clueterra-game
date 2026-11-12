package io.clueterra.questions.service.hint;

import io.clueterra.questions.clients.common.CountryReference;
import io.clueterra.questions.entity.Country;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CountryNameMatcher {

    private static final int MIN_NAME_LENGTH = 3;
    private static final Set<String> GENERIC_NAME_WORDS = Set.of(
            "the", "and", "of", "islands", "island", "republic", "rep.", "kingdom", "territory", "state", "states",
            "democratic", "dem.", "federal", "fed.", "federated", "united", "people's", "peoples", "plurinational",
            "islamic", "arab", "socialist", "commonwealth", "union", "sar", "is.");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NAME_PARTS_SEPARATOR = Pattern.compile("\\s+and\\s+|,\\s*");
    private static final Map<String, Pattern> PHRASE_PATTERNS = new ConcurrentHashMap<>();

    private CountryNameMatcher() {
    }

    public static boolean revealsCountry(String text, Country country) {
        if (text == null || text.isBlank() || country == null) {
            return false;
        }
        return nameVariants(country).stream().anyMatch(name -> mentions(text, name));
    }

    public static boolean mentions(String text, String name) {
        if (text == null || name == null || name.isBlank() || name.trim().length() < MIN_NAME_LENGTH) {
            return false;
        }
        return phrasePattern(name).matcher(text).find();
    }

    public static List<String> nameVariants(Country country) {
        Set<String> names = new LinkedHashSet<>();
        String nameEn = country.getNameEn();
        addIfPresent(names, nameEn);
        addIfPresent(names, country.getNameNative());
        if (nameEn != null) {
            if (nameEn.contains("(")) {
                addIfPresent(names, nameEn.substring(0, nameEn.indexOf('(')));
            }
            for (String part : NAME_PARTS_SEPARATOR.split(nameEn)) {
                String trimmed = part.trim();
                if (trimmed.length() >= 4 && isDistinctive(trimmed)) {
                    addIfPresent(names, trimmed);
                }
            }
        }

        String referenceName = country.getIsoAlpha2() != null
                ? CountryReference.getCountryName(country.getIsoAlpha2())
                : null;
        addIfPresent(names, referenceName);
        if (referenceName != null) {
            addIfPresent(names, CountryReference.ADJECTIVE_FORMS.get(referenceName));
            List<String> variations = CountryReference.NAME_VARIATIONS.get(referenceName);
            if (variations != null) {
                variations.forEach(variation -> addIfPresent(names, variation));
            }
        }
        return List.copyOf(names);
    }

    private static boolean isDistinctive(String namePart) {
        return WHITESPACE.splitAsStream(namePart.toLowerCase(Locale.ROOT))
                .anyMatch(word -> !GENERIC_NAME_WORDS.contains(word));
    }

    private static void addIfPresent(Set<String> names, String value) {
        if (value != null && !value.isBlank()) {
            names.add(value.trim());
        }
    }

    private static Pattern phrasePattern(String phrase) {
        return PHRASE_PATTERNS.computeIfAbsent(phrase.trim().toLowerCase(Locale.ROOT), key -> {
            String body = WHITESPACE.splitAsStream(key)
                    .filter(token -> !token.isBlank())
                    .map(Pattern::quote)
                    .collect(Collectors.joining("\\s+"));
            return Pattern.compile("(?<![\\p{L}\\p{N}])" + body + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        });
    }
}
