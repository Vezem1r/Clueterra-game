package io.clueterra.questions.entity.enums;

import lombok.Getter;

@Getter
public enum DataSource {

    WORLD_BANK("World Bank API", "world-bank", 1, true, true),
    WIKIDATA("Wikidata", "wikidata", 2, true, true),
    CURATED_COUNTRY_FACTS("Curated Country Facts", "curated-country-facts", 3, true, true),
    GEONAMES("GeoNames API", "geonames", 4, true, true),
    OPEN_STREET_MAP("OpenStreetMap Overpass", "open-street-map", 5, true, true),
    CIA_FACTBOOK("CIA World Factbook", "cia-factbook", 6, true, true),
    NAGER_DATE("Nager.Date Holidays API", "nager-date", 7, true, true),
    BIG_MAC_INDEX("Big Mac Index (Economist)", "bigmacindex", 8, true, true),
    FAST_FOOD("Fast Food Data", "fast-food", 9, true, true),
    GLOBAL_BRANDS("Global Brands", "global-brands", 10, true, true),
    WIKIMEDIA_COMMONS("Wikimedia Commons", "wikimedia-commons", 11, false, false),
    CITYBIKES("CityBikes API", "citybikes", 13, true, true),
    MAPILLARY("Mapillary API", "mapillary", 14, true, true),
    OUR_WORLD_IN_DATA("Our World in Data", "our-world-in-data", 15, true, true),
    COMBINATORICS("Combinatorics Hint Engine", "combinatorics", 16, false, false),
    AI_GENERATED("AI Generated", "ai-generated", 17, false, false),
    NATURAL_EARTH("Natural Earth", "natural-earth", 18, false, true),
    HYDRO_RIVERS("HydroRIVERS", "hydro-rivers", 19, false, true),
    MANUAL("Manual Entry", "manual", 20, false, true),
    BUDGET_PROJECTS("BudgetGuessr Projects", "budget-projects", 21, true, true);

    private final String displayName;
    private final String configKey;
    private final int priority;
    private final boolean syncSupported;
    private final boolean hintGenerationSupported;

    DataSource(String displayName, String configKey, int priority,
               boolean syncSupported, boolean hintGenerationSupported) {
        this.displayName = displayName;
        this.configKey = configKey;
        this.priority = priority;
        this.syncSupported = syncSupported;
        this.hintGenerationSupported = hintGenerationSupported;
    }
}
