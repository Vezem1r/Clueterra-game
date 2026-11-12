package io.clueterra.questions.clients.curatedfacts;

import io.clueterra.questions.clients.common.CountryReference;
import io.clueterra.questions.entity.Country;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class CuratedFactTextSupport {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<CuratedCountryFactIndicator> LIST_INDICATORS = Set.of(
            CuratedCountryFactIndicator.SIGNATURE_DISH,
            CuratedCountryFactIndicator.SIGNATURE_DRINK,
            CuratedCountryFactIndicator.TRADITIONAL_DANCE,
            CuratedCountryFactIndicator.TRADITIONAL_INSTRUMENT,
            CuratedCountryFactIndicator.PROMINENT_SPORT
    );

    private CuratedFactTextSupport() {
    }

    static Optional<String> prepareValue(Country country,
                                         CuratedCountryFactIndicator indicator,
                                         String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        if (!LIST_INDICATORS.contains(indicator)) {
            return revealsCountry(rawValue, country)
                    ? Optional.empty()
                    : Optional.of(rawValue.trim());
        }

        List<String> safeItems = splitList(rawValue).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .filter(item -> !revealsCountry(item, country))
                .toList();
        return safeItems.isEmpty()
                ? Optional.empty()
                : Optional.of(String.join(", ", safeItems));
    }

    static String formatValue(CuratedCountryFactIndicator indicator, String value) {
        if (!LIST_INDICATORS.contains(indicator)) {
            return value;
        }
        return joinNaturalLanguage(splitList(value));
    }

    static String limitItems(CuratedCountryFactIndicator indicator, String value, int maxItems) {
        if (!LIST_INDICATORS.contains(indicator) || maxItems < 1) {
            return value;
        }
        List<String> items = splitList(value);
        return String.join(", ", items.subList(0, Math.min(items.size(), maxItems)));
    }

    static String formatWithDefiniteArticles(List<String> values) {
        return joinNaturalLanguage(values.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(CuratedFactTextSupport::withDefiniteArticle)
                .toList());
    }

    static String joinNaturalLanguage(List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.getFirst();
        }
        if (items.size() == 2) {
            return items.get(0) + " and " + items.get(1);
        }
        return String.join(", ", items.subList(0, items.size() - 1))
                + ", and " + items.getLast();
    }

    static boolean materiallyEquivalent(String first, String second, Country country) {
        Set<String> firstTokens = meaningfulTokens(first, country);
        Set<String> secondTokens = meaningfulTokens(second, country);
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) {
            return normalize(first).equals(normalize(second));
        }
        if (firstTokens.equals(secondTokens)) {
            return true;
        }

        Set<String> smaller = firstTokens.size() <= secondTokens.size() ? firstTokens : secondTokens;
        Set<String> larger = firstTokens.size() <= secondTokens.size() ? secondTokens : firstTokens;
        if (!larger.containsAll(smaller)) {
            return false;
        }
        return smaller.size() > 1 || smaller.stream().anyMatch(token -> token.length() >= 5);
    }

    static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenthesesDepth = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '(') {
                parenthesesDepth++;
            } else if (character == ')' && parenthesesDepth > 0) {
                parenthesesDepth--;
            }

            if (character == ',' && parenthesesDepth == 0) {
                addItem(items, current);
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        addItem(items, current);
        return List.copyOf(items);
    }

    private static void addItem(List<String> items, StringBuilder current) {
        String item = current.toString().trim();
        if (!item.isBlank()) {
            items.add(item);
        }
    }

    private static String withDefiniteArticle(String value) {
        return value.regionMatches(true, 0, "the ", 0, 4)
                ? value
                : "the " + value;
    }

    private static boolean revealsCountry(String value, Country country) {
        if (country == null || value == null || value.isBlank()) {
            return false;
        }

        String normalizedValue = normalize(value);
        if (normalizedValue.isBlank()) {
            return false;
        }

        Set<String> aliases = countryAliases(country);
        List<String> valueTokens = Arrays.stream(normalizedValue.split(" "))
                .filter(token -> !token.isBlank())
                .toList();

        for (String alias : aliases) {
            String normalizedAlias = normalize(alias);
            if (normalizedAlias.length() < 4) {
                continue;
            }
            if (containsPhrase(normalizedValue, normalizedAlias)) {
                return true;
            }

            String compactAlias = normalizedAlias.replace(" ", "");
            String singularAlias = compactAlias.endsWith("s") && compactAlias.length() > 5
                    ? compactAlias.substring(0, compactAlias.length() - 1)
                    : compactAlias;
            if (valueTokens.stream().anyMatch(token ->
                    token.length() >= singularAlias.length() && token.startsWith(singularAlias))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> countryAliases(Country country) {
        Set<String> aliases = new LinkedHashSet<>();
        addAlias(aliases, country.getNameEn());
        addAlias(aliases, country.getNameNative());

        String referenceName = CountryReference.getCountryName(country.getIsoAlpha2());
        addAlias(aliases, referenceName);
        if (referenceName != null) {
            CountryReference.getNameVariations(referenceName).forEach(alias -> addAlias(aliases, alias));
            addAlias(aliases, CountryReference.ADJECTIVE_FORMS.get(referenceName));
            addCompoundAliases(aliases, referenceName);
        }
        addCompoundAliases(aliases, country.getNameEn());
        return Set.copyOf(aliases);
    }

    private static void addCompoundAliases(Set<String> aliases, String countryName) {
        if (countryName == null || countryName.isBlank()) {
            return;
        }
        String withoutArticle = countryName.replaceFirst("(?i)^the\\s+", "");
        addAlias(aliases, withoutArticle);

        int conjunction = withoutArticle.toLowerCase(Locale.ROOT).indexOf(" and ");
        if (conjunction > 0) {
            addAlias(aliases, withoutArticle.substring(0, conjunction));
        }

        if (withoutArticle.regionMatches(true, 0, "Saint ", 0, 6)) {
            addAlias(aliases, "St " + withoutArticle.substring(6));
        } else if (withoutArticle.regionMatches(true, 0, "St. ", 0, 4)) {
            addAlias(aliases, "Saint " + withoutArticle.substring(4));
            addAlias(aliases, "St " + withoutArticle.substring(4));
        }
    }

    private static void addAlias(Set<String> aliases, String alias) {
        if (alias != null && !alias.isBlank()) {
            aliases.add(alias.trim());
        }
    }

    private static boolean containsPhrase(String normalizedValue, String normalizedAlias) {
        return (" " + normalizedValue + " ").contains(" " + normalizedAlias + " ");
    }

    private static Set<String> meaningfulTokens(String value, Country country) {
        Set<String> countryTokens = countryAliases(country).stream()
                .map(CuratedFactTextSupport::normalize)
                .flatMap(alias -> Arrays.stream(alias.split(" ")))
                .filter(token -> token.length() >= 3)
                .collect(Collectors.toSet());

        return Arrays.stream(normalize(value).split(" "))
                .filter(token -> token.length() >= 3)
                .filter(token -> !countryTokens.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim();
    }
}
