package io.clueterra.questions.clients.brands.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BrandConstants {

    private BrandConstants() {
    }

    private static final Map<String, List<String>> COUNTRY_NAME_FRAGMENTS = Map.ofEntries(
            Map.entry("QA", List.of("qatar")),
            Map.entry("TR", List.of("turk")),
            Map.entry("TH", List.of("thai")),
            Map.entry("SG", List.of("singapore")),
            Map.entry("SA", List.of("saudi")),
            Map.entry("AE", List.of("emirat", "dubai")),
            Map.entry("DE", List.of("deutsch")),
            Map.entry("NZ", List.of("zealand")),
            Map.entry("ZA", List.of("south afric")),
            Map.entry("KR", List.of("korea")),
            Map.entry("CN", List.of("china", "chinese")),
            Map.entry("IN", List.of("india")),
            Map.entry("JP", List.of("japan")),
            Map.entry("GB", List.of("british", "britain", "english")),
            Map.entry("FR", List.of("french", "france")),
            Map.entry("IT", List.of("italian", "italy")),
            Map.entry("ES", List.of("spanish", "spain")),
            Map.entry("AU", List.of("australia")),
            Map.entry("RU", List.of("russia")),
            Map.entry("NO", List.of("norweg")),
            Map.entry("SE", List.of("swed")),
            Map.entry("BR", List.of("brazil")),
            Map.entry("MX", List.of("mexic")),
            Map.entry("CA", List.of("canad")),
            Map.entry("IE", List.of("irish", "ireland")),
            Map.entry("CH", List.of("swiss"))
    );

    public static boolean brandContainsCountryName(String brandName, String countryCode) {
        List<String> fragments = COUNTRY_NAME_FRAGMENTS.get(countryCode);
        if (fragments == null) return false;
        String lower = brandName.toLowerCase();
        return fragments.stream().anyMatch(lower::contains);
    }

    public static final Set<String> TECH_INDUSTRIES = Set.of("tech");
    public static final Set<String> AUTOMOTIVE_INDUSTRIES = Set.of("automotive");
    public static final Set<String> FASHION_INDUSTRIES = Set.of("fashion");
    public static final Set<String> LUXURY_INDUSTRIES = Set.of("luxury");
    public static final Set<String> FOOD_INDUSTRIES = Set.of("food_beverage");
    public static final Set<String> ENTERTAINMENT_INDUSTRIES = Set.of("entertainment");
    public static final Set<String> FURNITURE_INDUSTRIES = Set.of("furniture", "lifestyle");
    public static final Set<String> ENERGY_INDUSTRIES = Set.of("energy");
    public static final Set<String> FINANCE_INDUSTRIES = Set.of("finance");
    public static final Set<String> TRAVEL_INDUSTRIES = Set.of("travel");
    public static final Set<String> GAMING_INDUSTRIES = Set.of("gaming");

    public static final int MIN_BRANDS_FOR_INDUSTRY_HINT = 2;
    public static final int HIGH_RECOGNITION_THRESHOLD = 8;

    public static final Map<String, String> INDUSTRY_VERBS = Map.ofEntries(
            Map.entry("tech", "connects and communicates"),
            Map.entry("automotive", "drives"),
            Map.entry("fashion", "dresses"),
            Map.entry("luxury", "expresses style"),
            Map.entry("food_beverage", "eats and drinks"),
            Map.entry("entertainment", "plays and watches"),
            Map.entry("gaming", "plays and competes"),
            Map.entry("furniture", "furnishes homes"),
            Map.entry("finance", "pays and banks"),
            Map.entry("travel", "travels"),
            Map.entry("energy", "powers its economy"),
            Map.entry("retail", "shops"),
            Map.entry("aerospace", "flies"),
            Map.entry("pharma", "heals"),
            Map.entry("logistics", "ships goods"),
            Map.entry("conglomerate", "builds industry")
    );
}
