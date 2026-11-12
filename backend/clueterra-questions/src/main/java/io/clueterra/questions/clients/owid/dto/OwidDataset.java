package io.clueterra.questions.clients.owid.dto;

import io.clueterra.questions.entity.enums.Category;
import java.util.Set;

public enum OwidDataset {

    HAPPINESS(
            "Life Satisfaction Score",
            "OWID_HAPPINESS",
            "happiness-cantril-ladder",
            "Self-reported life satisfaction",
            "Cantril ladder score",
            Category.DEMOGRAPHICS,
            "score",
            false,
            0.88,
            0.85,
            majorPolicy(
                    "Wellbeing Research Centre (2026) – with major processing by Our World in Data",
                    "happiness-cantril-ladder",
                    Set.of("Wellbeing Research Centre"))),

    HDI(
            "Human Development Index",
            "OWID_HDI",
            "human-development-index",
            "Human Development Index",
            "Human Development Index",
            Category.DEMOGRAPHICS,
            "index",
            false,
            0.90,
            0.88,
            directPolicy(
                    "UNDP, Human Development Report (2025) – with minor processing by Our World in Data",
                    "human-development-index",
                    "CC BY 3.0 IGO",
                    "https://hdr.undp.org/copyright-and-terms-use",
                    "minor",
                    Set.of("UNDP, Human Development Report"))),

    DEMOCRACY(
            "Liberal Democracy Index",
            "OWID_DEMOCRACY",
            "liberal-democracy",
            "Liberal democracy index",
            "Liberal democracy index (central estimate)",
            Category.POLITICS,
            "index",
            false,
            0.84,
            0.82,
            directPolicy(
                    "V-Dem (2026) – processed by Our World in Data",
                    "liberal-democracy",
                    "CC BY 4.0",
                    "https://creativecommons.org/licenses/by/4.0/",
                    "",
                    Set.of("V-Dem"))),

    SOLAR_PCT(
            "Solar Energy Share",
            "OWID_SOLAR_PCT",
            "solar-share-energy",
            "Solar",
            "Solar as a share of total energy supply",
            Category.ENVIRONMENT,
            "%",
            false,
            0.88,
            0.84,
            energyPolicy("solar-share-energy")),

    WIND_PCT(
            "Wind Energy Share",
            "OWID_WIND_PCT",
            "wind-share-energy",
            "Wind",
            "Wind as a share of total energy supply",
            Category.ENVIRONMENT,
            "%",
            false,
            0.88,
            0.84,
            energyPolicy("wind-share-energy")),

    RENEWABLES_PCT(
            "Renewable Energy Share",
            "OWID_RENEWABLES_PCT",
            "renewable-share-energy",
            "Renewables",
            "Renewables as a share of total energy supply",
            Category.ENVIRONMENT,
            "%",
            false,
            0.88,
            0.86,
            energyPolicy("renewable-share-energy")),

    POVERTY_SHARE(
            "Extreme Poverty Share",
            "OWID_POVERTY_SHARE",
            "share-of-population-in-extreme-poverty",
            "Share of population in poverty ($3 a day)",
            "Share of population in poverty ($3 a day, 2021 prices)",
            Category.DEMOGRAPHICS,
            "%",
            true,
            0.86,
            0.84,
            majorPolicy(
                    "World Bank Poverty and Inequality Platform (2026) – with major processing by Our World in Data",
                    "share-of-population-in-extreme-poverty",
                    Set.of("World Bank Poverty and Inequality Platform"))),

    CO2_PER_CAPITA(
            "CO₂ Emissions Per Capita",
            "OWID_CO2_PER_CAPITA",
            "co-emissions-per-capita",
            "CO₂ emissions per capita",
            "Annual CO₂ emissions (per capita)",
            Category.ENVIRONMENT,
            "tonnes",
            false,
            0.90,
            0.88,
            majorPolicy(
                    "Global Carbon Budget (2025); Population based on various sources (2024) – with major processing by Our World in Data",
                    "co-emissions-per-capita",
                    Set.of("Global Carbon Project", "Various sources"))),

    LIFE_EXPECTANCY(
            "Life Expectancy at Birth",
            "OWID_LIFE_EXPECTANCY",
            "life-expectancy",
            "Life expectancy",
            "Period life expectancy at birth",
            Category.DEMOGRAPHICS,
            "years",
            false,
            0.92,
            0.90,
            majorPolicy(
                    "Riley (2005); Zijdeman et al. (2015); HMD (2025); UN WPP (2024) – with major processing by Our World in Data",
                    "life-expectancy",
                    Set.of("Human Mortality Database", "United Nations", "Zijdeman et al.", "James C. Riley"))),

    FERTILITY_RATE(
            "Fertility Rate",
            "OWID_FERTILITY_RATE",
            "children-per-woman-UN",
            "Fertility rate",
            "Fertility rate - Sex: all - Age: all - Variant: estimates",
            Category.DEMOGRAPHICS,
            "children/woman",
            false,
            0.90,
            0.86,
            directPolicy(
                    "UN, World Population Prospects (2024) – processed by Our World in Data",
                    "children-per-woman-UN",
                    "CC BY 3.0 IGO",
                    "https://population.un.org/wpp/downloads/",
                    "",
                    Set.of("United Nations"))),

    INTERNET_USERS(
            "Internet Users",
            "OWID_INTERNET_USERS",
            "share-of-individuals-using-the-internet",
            "Share of the population using the Internet",
            "Individuals using the Internet (% of population)",
            Category.CULTURE,
            "%",
            false,
            0.88,
            0.84,
            directPolicy(
                    "World Telecommunication/ICT Indicators Database - International Telecommunication Union (ITU), via World Bank (2026) – with minor processing by Our World in Data",
                    "share-of-individuals-using-the-internet",
                    "CC BY 4.0",
                    "https://datacatalog.worldbank.org/search/dataset/0037712/World-Development-Indicators",
                    "minor",
                    Set.of("World Telecommunication/ICT Indicators Database - International Telecommunication Union (ITU), via World Bank"))),

    ALCOHOL_CONSUMPTION(
            "Alcohol Consumption Per Capita",
            "OWID_ALCOHOL_CONSUMPTION",
            "total-alcohol-consumption-per-capita-litres-of-pure-alcohol",
            "Alcohol consumption",
            "Total alcohol consumption per capita (liters of pure alcohol, projected estimates, 15+ years of age)",
            Category.HEALTH,
            "litres/year",
            false,
            0.84,
            0.80,
            directPolicy(
                    "Global Health Observatory Data Repository - World Health Organization (WHO), via World Bank (2026) – processed by Our World in Data",
                    "total-alcohol-consumption-per-capita-litres-of-pure-alcohol",
                    "CC BY 4.0",
                    "https://datacatalog.worldbank.org/search/dataset/0037712/World-Development-Indicators",
                    "",
                    Set.of("Global Health Observatory Data Repository - World Health Organization (WHO), via World Bank")));

    private static final String GRAPHER_BASE_URL = "https://ourworldindata.org/grapher/";

    private final String displayName;
    private final String indicatorCode;
    private final String chartSlug;
    private final String csvColumn;
    private final String metadataColumn;
    private final Category category;
    private final String unit;
    private final boolean lowerIsBetter;
    private final double confidence;
    private final double weight;
    private final OwidLicensePolicy licensePolicy;

    OwidDataset(
            String displayName,
            String indicatorCode,
            String chartSlug,
            String csvColumn,
            String metadataColumn,
            Category category,
            String unit,
            boolean lowerIsBetter,
            double confidence,
            double weight,
            OwidLicensePolicy licensePolicy) {
        this.displayName = displayName;
        this.indicatorCode = indicatorCode;
        this.chartSlug = chartSlug;
        this.csvColumn = csvColumn;
        this.metadataColumn = metadataColumn;
        this.category = category;
        this.unit = unit;
        this.lowerIsBetter = lowerIsBetter;
        this.confidence = confidence;
        this.weight = weight;
        this.licensePolicy = licensePolicy;
    }

    public String displayName() {
        return displayName;
    }

    public String indicatorCode() {
        return indicatorCode;
    }

    public String csvUrl() {
        return GRAPHER_BASE_URL + chartSlug + ".csv";
    }

    public String metadataUrl() {
        return GRAPHER_BASE_URL + chartSlug + ".metadata.json";
    }

    public String csvColumn() {
        return csvColumn;
    }

    public String metadataColumn() {
        return metadataColumn;
    }

    public Category category() {
        return category;
    }

    public String unit() {
        return unit;
    }

    public boolean isLowerIsBetter() {
        return lowerIsBetter;
    }

    public double confidence() {
        return confidence;
    }

    public double weight() {
        return weight;
    }

    public OwidLicensePolicy licensePolicy() {
        return licensePolicy;
    }

    private static OwidLicensePolicy majorPolicy(
            String attribution, String chartSlug, Set<String> expectedProducers) {
        return new OwidLicensePolicy(
                attribution,
                "https://ourworldindata.org/grapher/" + chartSlug,
                "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0/",
                Set.of("major"),
                expectedProducers,
                Set.of());
    }

    private static OwidLicensePolicy energyPolicy(String chartSlug) {
        return majorPolicy(
                "Energy Institute - Statistical Review of World Energy (2026); Smil (2017); U.S. Energy Information Administration (2026) – with major processing by Our World in Data",
                chartSlug,
                Set.of(
                        "Energy Institute",
                        "Smil",
                        "U.S. Energy Information Administration"));
    }

    private static OwidLicensePolicy directPolicy(
            String attribution,
            String chartSlug,
            String licenseLabel,
            String licenseUrl,
            String processingLevel,
            Set<String> expectedProducers) {
        return new OwidLicensePolicy(
                attribution,
                "https://ourworldindata.org/grapher/" + chartSlug,
                licenseLabel,
                licenseUrl,
                Set.of(processingLevel),
                expectedProducers,
                Set.of(licenseUrl));
    }
}
