package io.clueterra.questions.clients.wikidata.hintgeneration;

import io.clueterra.questions.clients.common.CountryReference;
import io.clueterra.questions.entity.Country;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class WikidataHintValueSupport {

    private static final int MAX_PLUG_TYPES = 3;
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final String GENERIC_BRITISH_PLUG_GROUP =
            "AC power plugs and sockets: British and related types";
    private static final Map<String, String> FRIENDLY_PLUG_TYPES = Map.ofEntries(
            Map.entry("as/nzs 3112", "Type I"),
            Map.entry("bs 1363", "Type G"),
            Map.entry("bs 546", "Type D or M"),
            Map.entry("europlug", "Type C"),
            Map.entry("iec 60906-1", "Type N"),
            Map.entry("nema 1-15", "Type A"),
            Map.entry("nema 5-15", "Type B"),
            Map.entry("schuko", "Type F"),
            Map.entry("sn 441011", "Type J")
    );
    private static final Set<String> GENERIC_ANTHEM_TITLES = Set.of(
            "anthem",
            "europe",
            "freedom",
            "homeland",
            "liberte",
            "liberty",
            "motherland",
            "national anthem",
            "patria"
    );
    private static final Set<String> GENERIC_COUNTRY_NAME_TOKENS = Set.of(
            "central",
            "democratic",
            "federated",
            "islands",
            "kingdom",
            "north",
            "republic",
            "south",
            "states",
            "united"
    );

    private WikidataHintValueSupport() {
    }

    static Optional<String> prepareAnthem(Country country, String rawValue) {
        if (rawValue == null || rawValue.isBlank() || revealsCountry(rawValue, country)) {
            return Optional.empty();
        }

        String value = rawValue.trim();
        return GENERIC_ANTHEM_TITLES.contains(normalize(value))
                ? Optional.empty()
                : Optional.of(value);
    }

    static Optional<String> prepareDomain(Country country, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> !revealsCountry(value, country))
                .map(value -> value.startsWith(".") ? value : "." + value)
                .findFirst();
    }

    static Optional<String> prepareSimpleValue(String rawValue) {
        return rawValue == null || rawValue.isBlank()
                ? Optional.empty()
                : Optional.of(rawValue.trim());
    }

    static List<String> preparePlugTypes(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }

        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> !GENERIC_BRITISH_PLUG_GROUP.equalsIgnoreCase(value))
                .map(WikidataHintValueSupport::friendlyPlugType)
                .distinct()
                .limit(MAX_PLUG_TYPES)
                .toList();
    }

    static String formatPlugTypes(List<String> plugTypes) {
        if (plugTypes.isEmpty()) {
            return "";
        }
        if (plugTypes.size() == 1) {
            return plugTypes.getFirst();
        }
        if (plugTypes.size() == 2) {
            return plugTypes.getFirst() + " and " + plugTypes.getLast();
        }
        return String.join(", ", plugTypes.subList(0, plugTypes.size() - 1))
                + ", and " + plugTypes.getLast();
    }

    private static String friendlyPlugType(String value) {
        String friendly = FRIENDLY_PLUG_TYPES.get(value.toLowerCase(Locale.ROOT));
        if (friendly != null) {
            return friendly;
        }
        if (value.regionMatches(true, 0, "Type ", 0, 5) && value.length() == 6) {
            return "Type " + Character.toUpperCase(value.charAt(5));
        }
        return value;
    }

    private static boolean revealsCountry(String value, Country country) {
        if (country == null) {
            return false;
        }

        String normalizedValue = normalize(value);
        List<String> valueTokens = Arrays.stream(normalizedValue.split(" "))
                .filter(token -> !token.isBlank())
                .toList();
        return countryAliases(country).stream()
                .map(WikidataHintValueSupport::normalize)
                .filter(alias -> alias.length() >= 4)
                .anyMatch(alias -> matchesAlias(normalizedValue, valueTokens, alias));
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
        }
        return Set.copyOf(aliases);
    }

    private static void addAlias(Set<String> aliases, String alias) {
        if (alias != null && !alias.isBlank()) {
            aliases.add(alias.trim());
        }
    }

    private static boolean containsPhrase(String normalizedValue, String normalizedAlias) {
        return (" " + normalizedValue + " ").contains(" " + normalizedAlias + " ");
    }

    private static boolean matchesAlias(String normalizedValue,
                                        List<String> valueTokens,
                                        String normalizedAlias) {
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

        return Arrays.stream(normalizedAlias.split(" "))
                .filter(token -> token.length() >= 5)
                .filter(token -> !GENERIC_COUNTRY_NAME_TOKENS.contains(token))
                .anyMatch(aliasToken -> valueTokens.stream().anyMatch(valueToken ->
                        valueToken.length() >= aliasToken.length() && valueToken.startsWith(aliasToken)));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutDiacritics = DIACRITICS.matcher(
                        Normalizer.normalize(value, Normalizer.Form.NFD))
                .replaceAll("");
        return NON_ALPHANUMERIC.matcher(withoutDiacritics.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim();
    }
}
