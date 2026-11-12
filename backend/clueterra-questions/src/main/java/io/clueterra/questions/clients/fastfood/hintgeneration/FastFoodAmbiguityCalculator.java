package io.clueterra.questions.clients.fastfood.hintgeneration;

import io.clueterra.questions.clients.fastfood.dto.FastFoodConstants;
import io.clueterra.questions.clients.fastfood.dto.FastFoodData;
import io.clueterra.questions.clients.fastfood.dto.CountryFastFood;

import java.util.Map;
import java.util.function.Predicate;

public final class FastFoodAmbiguityCalculator {

    private FastFoodAmbiguityCalculator() {
    }

    private static final int DEFAULT_AMBIGUITY = 30;

    public static int compute(String indicatorCode, String countryCode, FastFoodData allData) {
        if (allData == null || allData.countries() == null) return DEFAULT_AMBIGUITY;
        Map<String, CountryFastFood> countries = allData.countries();

        return switch (indicatorCode) {
            case String s when s.startsWith("FF_SIGNATURE_ITEM_") ->
                    countSignatureItemCountries(indicatorCode, countryCode, countries);
            case "FF_LOCAL_CHAMPION" -> countLocalChampionSameName(countryCode, countries);
            case "FF_ABSENT_CHAIN" -> countAbsentSameChain(countryCode, countries);
            case String s when s.startsWith("FF_CULTURAL_FACT_") -> 1;
            case "FF_KFC_CHRISTMAS" -> 1;
            case "FF_NO_MCDONALDS" -> countCountriesMatching(countries, c -> !c.chains().containsKey("mcdonalds"));
            case "FF_CHAIN_DESERT" -> countCountriesMatching(countries, c -> c.chains().isEmpty());
            case "FF_CHAIN_PARADISE" -> countCountriesMatching(countries, c -> c.chains().size() >= 15);
            case "FF_STARBUCKS_DESERT" -> countCountriesMatching(countries, c -> !c.chains().containsKey("starbucks"));
            case "FF_VEGGIE_MENU" -> countCountriesWithSpecialFact(countries, "VEGGIE_MENU");
            case "FF_HALAL_MENU" -> countCountriesWithSpecialFact(countries, "HALAL_MENU");
            case "FF_KOSHER_MENU" -> countCountriesWithSpecialFact(countries, "KOSHER_MENU");
            case "FF_RENAMED_CHAIN" -> countCountriesWithSpecialFact(countries, "RENAMED_CHAIN");
            case "FF_MOST_BEAUTIFUL_MCDONALDS" -> countCountriesWithSpecialFact(countries, "MOST_BEAUTIFUL_MCDONALDS");
            case "FF_ALCOHOL_MENU" -> countCountriesWithSpecialFact(countries, "ALCOHOL_MENU");
            case "FF_OLDEST_FRANCHISE" -> countCountriesWithSpecialFact(countries, "OLDEST_FRANCHISE");
            default -> DEFAULT_AMBIGUITY;
        };
    }

    private static int countSignatureItemCountries(String indicatorCode, String countryCode,
                                                   Map<String, CountryFastFood> countries) {
        CountryFastFood thisCountry = countries.get(countryCode);
        if (thisCountry == null || thisCountry.signatureItems().isEmpty()) return DEFAULT_AMBIGUITY;

        int index = parseIndex(indicatorCode);
        if (index >= thisCountry.signatureItems().size()) return DEFAULT_AMBIGUITY;

        String itemName = thisCountry.signatureItems().get(index).item();
        return countCountriesMatching(countries, c ->
                c.signatureItems().stream().anyMatch(si -> si.item().equals(itemName)));
    }

    private static int parseIndex(String indicatorCode) {
        try {
            return Integer.parseInt(indicatorCode.substring("FF_SIGNATURE_ITEM_".length())) - 1;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int countLocalChampionSameName(String countryCode, Map<String, CountryFastFood> countries) {
        CountryFastFood thisCountry = countries.get(countryCode);
        if (thisCountry == null || thisCountry.localChampion() == null) return DEFAULT_AMBIGUITY;
        String name = thisCountry.localChampion().name();
        if (name == null) return DEFAULT_AMBIGUITY;
        return countCountriesMatching(countries, c ->
                c.localChampion() != null && name.equals(c.localChampion().name()));
    }

    private static int countAbsentSameChain(String countryCode, Map<String, CountryFastFood> countries) {
        CountryFastFood thisCountry = countries.get(countryCode);
        if (thisCountry == null || thisCountry.absentChains().isEmpty()) return DEFAULT_AMBIGUITY;
        String mostNotable = thisCountry.absentChains().stream()
                .filter(FastFoodConstants.NOTABLE_ABSENT_REASONS::containsKey)
                .findFirst()
                .orElse(thisCountry.absentChains().getFirst());
        return countCountriesMatching(countries, c -> c.absentChains().contains(mostNotable));
    }

    public static int computeAbsentChain(String chain, Map<String, CountryFastFood> countries) {
        return countCountriesMatching(countries, c -> c.absentChains().contains(chain));
    }

    private static int countCountriesWithSpecialFact(Map<String, CountryFastFood> countries, String suffix) {
        return countCountriesMatching(countries, c -> c.specialFacts().containsKey(suffix));
    }

    private static int countCountriesMatching(Map<String, CountryFastFood> countries,
                                              Predicate<CountryFastFood> predicate) {
        return (int) countries.values().stream().filter(predicate).count();
    }
}
