package io.clueterra.questions.service.hint;

import io.clueterra.questions.clients.common.CountryReference;
import io.clueterra.questions.entity.Country;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CapitalNameSupport {

    private CapitalNameSupport() {
    }

    public static boolean capitalEchoesCountryName(Country country, String capital) {
        if (country == null || !StringUtils.hasText(capital)) {
            return false;
        }

        String normalizedCapital = normalizeComparable(capital);
        if (!StringUtils.hasText(normalizedCapital)) {
            return false;
        }

        for (String countryName : countryNameCandidates(country)) {
            String normalizedCountry = normalizeComparable(countryName);
            if (!StringUtils.hasText(normalizedCountry)) {
                continue;
            }
            if (normalizedCapital.equals(normalizedCountry)) {
                return true;
            }
            if (normalizedCapital.startsWith(normalizedCountry + " ")
                    || normalizedCountry.startsWith(normalizedCapital + " ")) {
                return true;
            }
            if (hasClosePrefixMatch(normalizedCapital, normalizedCountry)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> countryNameCandidates(Country country) {
        if (country == null) {
            return List.of();
        }

        Set<String> names = new LinkedHashSet<>();
        addIfPresent(names, country.getNameEn());
        addIfPresent(names, country.getNameNative());

        String canonical = country.getIsoAlpha2() != null
                ? CountryReference.getCountryName(country.getIsoAlpha2().toUpperCase(Locale.ROOT))
                : null;
        addIfPresent(names, canonical);
        if (StringUtils.hasText(canonical)) {
            CountryReference.getNameVariations(canonical).forEach(name -> addIfPresent(names, name));
        }

        return new ArrayList<>(names);
    }

    private static void addIfPresent(Set<String> names, String name) {
        if (StringUtils.hasText(name)) {
            names.add(name.trim());
        }
    }

    private static String normalizeComparable(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\b(the|city|capital|municipality|town)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private static boolean hasClosePrefixMatch(String normalizedCapital, String normalizedCountry) {
        int shorter = Math.min(normalizedCapital.length(), normalizedCountry.length());
        if (shorter < 5) {
            return false;
        }
        if (!normalizedCapital.startsWith(normalizedCountry) && !normalizedCountry.startsWith(normalizedCapital)) {
            return false;
        }
        return Math.abs(normalizedCapital.length() - normalizedCountry.length()) <= 3;
    }
}
