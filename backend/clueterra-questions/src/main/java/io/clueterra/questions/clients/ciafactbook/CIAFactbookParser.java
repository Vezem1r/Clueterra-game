package io.clueterra.questions.clients.ciafactbook;

import com.fasterxml.jackson.databind.JsonNode;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.entity.enums.Category;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CIAFactbookParser {

    private static final BigDecimal BASE_CONFIDENCE = BigDecimal.valueOf(0.90);
    private static final BigDecimal HIGH_CONFIDENCE = BigDecimal.valueOf(0.95);
    private static final BigDecimal MEDIUM_CONFIDENCE = BigDecimal.valueOf(0.85);

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\((\\d{4})(?:\\s*est\\.?)?\\)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(-?[\\d,]+\\.?\\d*)");
    private static final Pattern MULTIPLIER_PATTERN = Pattern.compile("([\\d.]+)\\s*(trillion|billion|million|thousand)", Pattern.CASE_INSENSITIVE);

    private static final String UNIT_PEOPLE = "people";
    private static final String UNIT_YEARS = "years";
    private static final String UNIT_PER_1000 = "per 1,000";
    private static final String UNIT_TOTAL_POPULATION = "total population";
    private static final String UNIT_LITERS = "liters";
    private static final String UNIT_PCT_GDP = "% of GDP";
    private static final String UNIT_CUBIC_METERS = "cubic meters";
    private static final String UNIT_PERCENT = "%";
    private static final String UNIT_USD = "USD";
    private static final String UNIT_KM = "km";

    private static final String FIELD_TOTAL = "total";

    public List<RawStatistic> parseAll(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        if (root == null || root.isMissingNode()) {
            return stats;
        }

        try {
            stats.addAll(parseGeography(root));
            stats.addAll(parsePeopleAndSociety(root));
            stats.addAll(parseGovernment(root));
            stats.addAll(parseEconomy(root));
            stats.addAll(parseEnergy(root));
            stats.addAll(parseCommunications(root));
            stats.addAll(parseTransportation(root));
            stats.addAll(parseMilitary(root));
            stats.addAll(parseSpace(root));
            stats.addAll(parseTransnationalIssues(root));
        } catch (Exception e) {
            log.error("Error parsing CIA factbook data", e);
        }

        return stats;
    }

    public List<RawStatistic> parseGeography(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        JsonNode geo = root.path("Geography");
        if (geo.isMissingNode()) {
            return stats;
        }

        addTextStat(stats, geo.path("Location"), "LOCATION", "Geographic Location", Category.GEOGRAPHY, BigDecimal.valueOf(0.75), HIGH_CONFIDENCE);
        addTextStat(stats, geo.path("Geographic coordinates"), "COORDINATES", "Geographic Coordinates", Category.GEOGRAPHY, BigDecimal.valueOf(0.40), HIGH_CONFIDENCE);

        JsonNode area = geo.path("Area");
        addNumericStat(stats, area.path("total "), "TOTAL_AREA", "Total Area", Category.GEOGRAPHY, "km²", BigDecimal.valueOf(0.55), HIGH_CONFIDENCE);
        addNumericStat(stats, area.path("land"), "LAND_AREA", "Land Area", Category.GEOGRAPHY, "km²", BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);
        addNumericStat(stats, area.path("water"), "WATER_AREA", "Water Area", Category.GEOGRAPHY, "km²", BigDecimal.valueOf(0.40), HIGH_CONFIDENCE);
        addTextStat(stats, geo.path("Area - comparative"), "AREA_COMPARATIVE", "Size Comparison", Category.GEOGRAPHY, BigDecimal.valueOf(0.85), HIGH_CONFIDENCE);

        JsonNode boundaries = geo.path("Land boundaries");
        addNumericStat(stats, boundaries.path(FIELD_TOTAL), "LAND_BOUNDARIES_TOTAL", "Total Land Boundaries", Category.GEOGRAPHY, UNIT_KM, BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);
        addTextStat(stats, boundaries.path("border countries"), "BORDER_COUNTRIES", "Bordering Countries", Category.GEOGRAPHY, BigDecimal.valueOf(0.90), HIGH_CONFIDENCE);
        addNumericStat(stats, geo.path("Coastline"), "COASTLINE", "Coastline Length", Category.GEOGRAPHY, UNIT_KM, BigDecimal.valueOf(0.50), HIGH_CONFIDENCE);
        addTextStat(stats, geo.path("Climate"), "CLIMATE", "Climate", Category.CLIMATE, BigDecimal.valueOf(0.65), HIGH_CONFIDENCE);
        addTextStat(stats, geo.path("Terrain"), "TERRAIN", "Terrain", Category.GEOGRAPHY, BigDecimal.valueOf(0.60), HIGH_CONFIDENCE);

        JsonNode elevation = geo.path("Elevation");
        addTextStat(stats, elevation.path("highest point"), "HIGHEST_POINT", "Highest Point", Category.GEOGRAPHY, BigDecimal.valueOf(0.80), HIGH_CONFIDENCE);
        addTextStat(stats, elevation.path("lowest point"), "LOWEST_POINT", "Lowest Point", Category.GEOGRAPHY, BigDecimal.valueOf(0.55), HIGH_CONFIDENCE);
        addNumericStat(stats, elevation.path("mean elevation"), "MEAN_ELEVATION", "Mean Elevation", Category.GEOGRAPHY, "m", BigDecimal.valueOf(0.40), HIGH_CONFIDENCE);
        addTextStat(stats, geo.path("Natural resources"), "NATURAL_RESOURCES", "Natural Resources", Category.GEOGRAPHY, BigDecimal.valueOf(0.55), HIGH_CONFIDENCE);

        JsonNode landUse = geo.path("Land use");
        addNumericStat(stats, landUse.path("agricultural land"), "AGRICULTURAL_LAND_PCT", "Agricultural Land", Category.GEOGRAPHY, UNIT_PERCENT, BigDecimal.valueOf(0.35), BASE_CONFIDENCE);
        addNumericStat(stats, landUse.path("forest"), "FOREST_LAND_PCT", "Forest Area", Category.GEOGRAPHY, UNIT_PERCENT, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);

        JsonNode lakes = geo.path("Major lakes (area sq km)");
        addTextStat(stats, lakes.path("fresh water lake(s)"), "MAJOR_FRESHWATER_LAKES", "Major Freshwater Lakes", Category.GEOGRAPHY, BigDecimal.valueOf(0.75), HIGH_CONFIDENCE);
        addTextStat(stats, lakes.path("salt water lake(s)"), "MAJOR_SALTWATER_LAKES", "Major Saltwater Lakes", Category.GEOGRAPHY, BigDecimal.valueOf(0.70), HIGH_CONFIDENCE);
        addTextStat(stats, geo.path("Major rivers (by length in km)"), "MAJOR_RIVERS", "Major Rivers", Category.GEOGRAPHY, BigDecimal.valueOf(0.85), HIGH_CONFIDENCE);
        addTextStat(stats, geo.path("Major watersheds (area sq km)"), "MAJOR_WATERSHEDS", "Major Watersheds", Category.GEOGRAPHY, BigDecimal.valueOf(0.50), HIGH_CONFIDENCE);
        addTextStat(stats, geo.path("Population distribution"), "POPULATION_DISTRIBUTION", "Population Distribution", Category.GEOGRAPHY, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addTextStat(stats, geo.path("Natural hazards"), "NATURAL_HAZARDS", "Natural Hazards", Category.GEOGRAPHY, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);
        addTextStat(stats, geo.path("Geography - note"), "GEOGRAPHY_NOTE", "Geographic Notes", Category.GEOGRAPHY, BigDecimal.valueOf(0.70), BASE_CONFIDENCE);

        return stats;
    }

    public List<RawStatistic> parsePeopleAndSociety(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        JsonNode people = root.path("People and Society");
        if (people.isMissingNode()) {
            return stats;
        }

        JsonNode population = people.path("Population");
        addNumericStat(stats, population.path(FIELD_TOTAL), "SP.POP.TOTL", "Total Population", Category.DEMOGRAPHICS, UNIT_PEOPLE, BigDecimal.valueOf(0.70), HIGH_CONFIDENCE);
        addNumericStat(stats, population.path("male"), "POPULATION_MALE", "Male Population", Category.DEMOGRAPHICS, UNIT_PEOPLE, BigDecimal.valueOf(0.30), HIGH_CONFIDENCE);
        addNumericStat(stats, population.path("female"), "POPULATION_FEMALE", "Female Population", Category.DEMOGRAPHICS, UNIT_PEOPLE, BigDecimal.valueOf(0.30), HIGH_CONFIDENCE);
        addTextStat(stats, people.path("Religions"), "RELIGIONS", "Religions", Category.CULTURE, BigDecimal.valueOf(0.55), BASE_CONFIDENCE);

        JsonNode ageStructure = people.path("Age structure");
        addNumericStat(stats, ageStructure.path("0-14 years"), "AGE_0_14_PCT", "Population 0-14 years", Category.DEMOGRAPHICS, UNIT_PERCENT, BigDecimal.valueOf(0.35), BASE_CONFIDENCE);
        addNumericStat(stats, ageStructure.path("15-64 years"), "AGE_15_64_PCT", "Population 15-64 years", Category.DEMOGRAPHICS, UNIT_PERCENT, BigDecimal.valueOf(0.30), BASE_CONFIDENCE);
        addNumericStat(stats, ageStructure.path("65 years and over"), "AGE_65_PLUS_PCT", "Population 65+ years", Category.DEMOGRAPHICS, UNIT_PERCENT, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);

        JsonNode medianAge = people.path("Median age");
        addNumericStat(stats, medianAge.path(FIELD_TOTAL), "MEDIAN_AGE", "Median Age", Category.DEMOGRAPHICS, UNIT_YEARS, BigDecimal.valueOf(0.50), HIGH_CONFIDENCE);
        addNumericStat(stats, people.path("Population growth rate"), "POPULATION_GROWTH_RATE", "Population Growth Rate", Category.DEMOGRAPHICS, UNIT_PERCENT, BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);
        addNumericStat(stats, people.path("Birth rate"), "BIRTH_RATE", "Birth Rate", Category.DEMOGRAPHICS, UNIT_PER_1000, BigDecimal.valueOf(0.40), HIGH_CONFIDENCE);
        addNumericStat(stats, people.path("Death rate"), "DEATH_RATE", "Death Rate", Category.DEMOGRAPHICS, UNIT_PER_1000, BigDecimal.valueOf(0.35), HIGH_CONFIDENCE);
        addNumericStat(stats, people.path("Net migration rate"), "NET_MIGRATION_RATE", "Net Migration Rate", Category.DEMOGRAPHICS, UNIT_PER_1000, BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);

        JsonNode urban = people.path("Urbanization");
        addNumericStat(stats, urban.path("urban population"), "URBAN_POPULATION_PCT", "Urban Population", Category.DEMOGRAPHICS, UNIT_PERCENT, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addNumericStat(stats, urban.path("rate of urbanization"), "URBANIZATION_RATE", "Urbanization Rate", Category.DEMOGRAPHICS, UNIT_PERCENT, BigDecimal.valueOf(0.30), BASE_CONFIDENCE);
        addTextStat(stats, people.path("Major urban areas - population"), "MAJOR_CITIES", "Major Cities", Category.DEMOGRAPHICS, BigDecimal.valueOf(0.92), HIGH_CONFIDENCE);

        JsonNode sexRatio = people.path("Sex ratio");
        addNumericStat(stats, sexRatio.path(UNIT_TOTAL_POPULATION), "SEX_RATIO_TOTAL", "Sex Ratio", Category.DEMOGRAPHICS, "males/female", BigDecimal.valueOf(0.30), BASE_CONFIDENCE);
        addNumericStat(stats, people.path("Mother's mean age at first birth"), "MOTHERS_MEAN_AGE_FIRST_BIRTH", "Mother's Mean Age at First Birth", Category.DEMOGRAPHICS, UNIT_YEARS, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        addNumericStat(stats, people.path("Maternal mortality ratio"), "MATERNAL_MORTALITY", "Maternal Mortality Ratio", Category.HEALTH, "per 100,000", BigDecimal.valueOf(0.45), BASE_CONFIDENCE);

        JsonNode infantMort = people.path("Infant mortality rate");
        addNumericStat(stats, infantMort.path(FIELD_TOTAL), "INFANT_MORTALITY_RATE", "Infant Mortality Rate", Category.HEALTH, UNIT_PER_1000, BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);

        JsonNode lifeExp = people.path("Life expectancy at birth");
        addNumericStat(stats, lifeExp.path(UNIT_TOTAL_POPULATION), "SP.DYN.LE00.IN", "Life Expectancy", Category.HEALTH, UNIT_YEARS, BigDecimal.valueOf(0.55), HIGH_CONFIDENCE);
        addNumericStat(stats, lifeExp.path("male"), "LIFE_EXPECTANCY_MALE", "Life Expectancy (Male)", Category.HEALTH, UNIT_YEARS, BigDecimal.valueOf(0.35), HIGH_CONFIDENCE);
        addNumericStat(stats, lifeExp.path("female"), "LIFE_EXPECTANCY_FEMALE", "Life Expectancy (Female)", Category.HEALTH, UNIT_YEARS, BigDecimal.valueOf(0.35), HIGH_CONFIDENCE);
        addNumericStat(stats, people.path("Total fertility rate"), "FERTILITY_RATE", "Fertility Rate", Category.DEMOGRAPHICS, "children/woman", BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);

        JsonNode healthExp = people.path("Health expenditure");
        addNumericStat(stats, healthExp.path("Health expenditure (as % of GDP)"), "HEALTH_EXPENDITURE_GDP", "Health Expenditure (% of GDP)", Category.HEALTH, UNIT_PERCENT, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        addNumericStat(stats, people.path("Physician density"), "PHYSICIAN_DENSITY", "Physicians per 1,000", Category.HEALTH, UNIT_PER_1000, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        addNumericStat(stats, people.path("Hospital bed density"), "HOSPITAL_BED_DENSITY", "Hospital Beds per 1,000", Category.HEALTH, UNIT_PER_1000, BigDecimal.valueOf(0.35), BASE_CONFIDENCE);
        addNumericStat(stats, people.path("Obesity - adult prevalence rate"), "OBESITY_RATE", "Adult Obesity Rate", Category.HEALTH, UNIT_PERCENT, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);

        JsonNode alcohol = people.path("Alcohol consumption per capita");
        addNumericStat(stats, alcohol.path(FIELD_TOTAL), "ALCOHOL_CONSUMPTION_TOTAL", "Alcohol Consumption (Total)", Category.CULTURE, UNIT_LITERS, BigDecimal.valueOf(0.60), BASE_CONFIDENCE);
        addNumericStat(stats, alcohol.path("beer"), "ALCOHOL_CONSUMPTION_BEER", "Beer Consumption", Category.CULTURE, UNIT_LITERS, BigDecimal.valueOf(0.70), BASE_CONFIDENCE);
        addNumericStat(stats, alcohol.path("wine"), "ALCOHOL_CONSUMPTION_WINE", "Wine Consumption", Category.CULTURE, UNIT_LITERS, BigDecimal.valueOf(0.65), BASE_CONFIDENCE);
        addNumericStat(stats, alcohol.path("spirits"), "ALCOHOL_CONSUMPTION_SPIRITS", "Spirits Consumption", Category.CULTURE, UNIT_LITERS, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);

        JsonNode tobacco = people.path("Tobacco use");
        addNumericStat(stats, tobacco.path(FIELD_TOTAL), "TOBACCO_USE_TOTAL", "Tobacco Use Rate", Category.HEALTH, UNIT_PERCENT, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);

        JsonNode eduExp = people.path("Education expenditure");
        addNumericStat(stats, eduExp.path("Education expenditure (% GDP)"), "EDUCATION_EXPENDITURE_GDP", "Education Expenditure (% GDP)", Category.EDUCATION, UNIT_PERCENT, BigDecimal.valueOf(0.35), BASE_CONFIDENCE);

        JsonNode literacy = people.path("Literacy");
        addNumericStat(stats, literacy.path(UNIT_TOTAL_POPULATION), "LITERACY_RATE", "Literacy Rate", Category.EDUCATION, UNIT_PERCENT, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);

        JsonNode schoolLife = people.path("School life expectancy (primary to tertiary education)");
        addNumericStat(stats, schoolLife.path(FIELD_TOTAL), "SCHOOL_LIFE_EXPECTANCY", "Expected Years of Schooling", Category.EDUCATION, UNIT_YEARS, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);

        return stats;
    }

    public List<RawStatistic> parseGovernment(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        JsonNode gov = root.path("Government");
        if (gov.isMissingNode()) {
            return stats;
        }

        JsonNode countryName = gov.path("Country name");
        addTextStat(stats, countryName.path("former"), "FORMER_NAME", "Former Name", Category.CULTURE, BigDecimal.valueOf(0.75), BASE_CONFIDENCE);
        addTextStat(stats, countryName.path("etymology"), "NAME_ETYMOLOGY", "Name Etymology", Category.CULTURE, BigDecimal.valueOf(0.60), BASE_CONFIDENCE);
        addTextStat(stats, gov.path("Government type"), "GOVERNMENT_TYPE", "Government Type", Category.TRIVIA, BigDecimal.valueOf(0.50), HIGH_CONFIDENCE);

        JsonNode capital = gov.path("Capital");
        addTextStat(stats, capital.path("name"), "CAPITAL_CITY", "Capital City", Category.GEOGRAPHY, BigDecimal.valueOf(0.95), HIGH_CONFIDENCE);
        addTextStat(stats, capital.path("geographic coordinates"), "CAPITAL_COORDINATES", "Capital Coordinates", Category.GEOGRAPHY, BigDecimal.valueOf(0.35), HIGH_CONFIDENCE);
        addTextStat(stats, capital.path("time difference"), "TIME_ZONE", "Time Zone (vs UTC)", Category.GEOGRAPHY, BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);
        addTextStat(stats, capital.path("etymology"), "CAPITAL_ETYMOLOGY", "Capital Name Etymology", Category.CULTURE, BigDecimal.valueOf(0.55), BASE_CONFIDENCE);
        addTextStat(stats, gov.path("Administrative divisions"), "ADMIN_DIVISIONS", "Administrative Divisions", Category.GEOGRAPHY, BigDecimal.valueOf(0.70), BASE_CONFIDENCE);
        addTextStat(stats, gov.path("Independence"), "INDEPENDENCE", "Independence", Category.TRIVIA, BigDecimal.valueOf(0.65), BASE_CONFIDENCE);

        JsonNode constitution = gov.path("Constitution");
        addTextStat(stats, constitution.path("history"), "CONSTITUTION_HISTORY", "Constitution History", Category.TRIVIA, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);
        addTextStat(stats, gov.path("Legal system"), "LEGAL_SYSTEM", "Legal System", Category.TRIVIA, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);

        JsonNode citizenship = gov.path("Citizenship");
        addTextStat(stats, citizenship.path("citizenship by birth"), "CITIZENSHIP_BY_BIRTH", "Citizenship by Birth", Category.TRIVIA, BigDecimal.valueOf(0.30), BASE_CONFIDENCE);
        addTextStat(stats, citizenship.path("residency requirement for naturalization"), "NATURALIZATION_YEARS", "Years for Naturalization", Category.TRIVIA, BigDecimal.valueOf(0.35), BASE_CONFIDENCE);
        addTextStat(stats, gov.path("Suffrage"), "VOTING_AGE", "Voting Age", Category.TRIVIA, BigDecimal.valueOf(0.30), BASE_CONFIDENCE);

        JsonNode executive = gov.path("Executive branch");
        addTextStat(stats, executive.path("chief of state"), "CHIEF_OF_STATE", "Chief of State", Category.TRIVIA, BigDecimal.valueOf(0.90), MEDIUM_CONFIDENCE);
        addTextStat(stats, executive.path("head of government"), "HEAD_OF_GOVERNMENT", "Head of Government", Category.TRIVIA, BigDecimal.valueOf(0.80), MEDIUM_CONFIDENCE);

        JsonNode legislative = gov.path("Legislative branch");
        addTextStat(stats, legislative.path("legislative structure"), "LEGISLATIVE_STRUCTURE", "Legislative Structure", Category.TRIVIA, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        addTextStat(stats, gov.path("Flag"), "FLAG_DESCRIPTION", "Flag Description", Category.CULTURE, BigDecimal.valueOf(0.92), HIGH_CONFIDENCE);
        addTextStat(stats, gov.path("National symbol(s)"), "NATIONAL_SYMBOLS", "National Symbols", Category.CULTURE, BigDecimal.valueOf(0.85), HIGH_CONFIDENCE);
        addTextStat(stats, gov.path("National color(s)"), "NATIONAL_COLORS", "National Colors", Category.CULTURE, BigDecimal.valueOf(0.88), HIGH_CONFIDENCE);

        JsonNode heritage = gov.path("National heritage");
        addNumericStat(stats, heritage.path("total World Heritage Sites"), "WORLD_HERITAGE_SITES_COUNT", "UNESCO World Heritage Sites", Category.CULTURE, "sites", BigDecimal.valueOf(0.70), HIGH_CONFIDENCE);
        addTextStat(stats, heritage.path("selected World Heritage Site locales"), "WORLD_HERITAGE_SITES", "Famous Heritage Sites", Category.CULTURE, BigDecimal.valueOf(0.85), HIGH_CONFIDENCE);
        addTextStat(stats, gov.path("International organization participation"), "INTERNATIONAL_ORGS", "International Organizations", Category.TRIVIA, BigDecimal.valueOf(0.25), BASE_CONFIDENCE);

        return stats;
    }

    public List<RawStatistic> parseEconomy(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        JsonNode econ = root.path("Economy");
        if (econ.isMissingNode()) {
            return stats;
        }

        addTextStat(stats, econ.path("Economic overview"), "ECONOMIC_OVERVIEW", "Economic Overview", Category.ECONOMY, BigDecimal.valueOf(0.65), BASE_CONFIDENCE);

        JsonNode gdpPPP = econ.path("Real GDP (purchasing power parity)");
        addNumericStat(stats, gdpPPP.path("Real GDP (purchasing power parity) 2024"), "GDP_PPP_2024", "GDP (PPP) 2024", Category.ECONOMY, UNIT_USD, BigDecimal.valueOf(0.60), HIGH_CONFIDENCE);
        addNumericStat(stats, gdpPPP.path("Real GDP (purchasing power parity) 2023"), "GDP_PPP_2023", "GDP (PPP) 2023", Category.ECONOMY, UNIT_USD, BigDecimal.valueOf(0.55), HIGH_CONFIDENCE);

        JsonNode gdpGrowth = econ.path("Real GDP growth rate");
        addNumericStat(stats, gdpGrowth.path("Real GDP growth rate 2024"), "GDP_GROWTH_2024", "GDP Growth Rate 2024", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);
        addNumericStat(stats, gdpGrowth.path("Real GDP growth rate 2023"), "GDP_GROWTH_2023", "GDP Growth Rate 2023", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.40), HIGH_CONFIDENCE);

        JsonNode gdpCapita = econ.path("Real GDP per capita");
        addNumericStat(stats, gdpCapita.path("Real GDP per capita 2024"), "NY.GDP.PCAP.CD", "GDP per Capita 2024", Category.ECONOMY, UNIT_USD, BigDecimal.valueOf(0.55), HIGH_CONFIDENCE);
        addNumericStat(stats, econ.path("GDP (official exchange rate)"), "GDP_OFFICIAL", "GDP (Official Exchange Rate)", Category.ECONOMY, UNIT_USD, BigDecimal.valueOf(0.55), HIGH_CONFIDENCE);

        JsonNode inflation = econ.path("Inflation rate (consumer prices)");
        addNumericStat(stats, inflation.path("Inflation rate (consumer prices) 2024"), "INFLATION_2024", "Inflation Rate 2024", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);
        addNumericStat(stats, inflation.path("Inflation rate (consumer prices) 2023"), "INFLATION_2023", "Inflation Rate 2023", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.40), HIGH_CONFIDENCE);

        JsonNode gdpSector = econ.path("GDP - composition, by sector of origin");
        addNumericStat(stats, gdpSector.path("agriculture"), "GDP_AGRICULTURE_PCT", "GDP from Agriculture", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addNumericStat(stats, gdpSector.path("industry"), "GDP_INDUSTRY_PCT", "GDP from Industry", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addNumericStat(stats, gdpSector.path("services"), "GDP_SERVICES_PCT", "GDP from Services", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        addTextStat(stats, econ.path("Agricultural products"), "AGRICULTURAL_PRODUCTS", "Agricultural Products", Category.ECONOMY, BigDecimal.valueOf(0.65), BASE_CONFIDENCE);
        addTextStat(stats, econ.path("Industries"), "INDUSTRIES", "Major Industries", Category.ECONOMY, BigDecimal.valueOf(0.70), BASE_CONFIDENCE);
        addNumericStat(stats, econ.path("Labor force"), "LABOR_FORCE", "Labor Force", Category.ECONOMY, UNIT_PEOPLE, BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);

        JsonNode unemployment = econ.path("Unemployment rate");
        addNumericStat(stats, unemployment.path("Unemployment rate 2024"), "UNEMPLOYMENT", "Unemployment Rate", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.50), HIGH_CONFIDENCE);

        JsonNode youthUnemp = econ.path("Youth unemployment rate (ages 15-24)");
        addNumericStat(stats, youthUnemp.path(FIELD_TOTAL), "YOUTH_UNEMPLOYMENT", "Youth Unemployment Rate", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addNumericStat(stats, econ.path("Population below poverty line"), "POVERTY_RATE", "Population Below Poverty Line", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);

        JsonNode gini = econ.path("Gini Index coefficient - distribution of family income");
        if (!gini.isMissingNode()) {
            gini.fields().forEachRemaining(entry -> {
                if (!entry.getKey().equalsIgnoreCase("note")) {
                    addNumericStat(stats, entry.getValue(), "GINI_INDEX",
                            "Gini Index", Category.ECONOMY, "",
                            BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
                }
            });
        }

        JsonNode budget = econ.path("Budget");
        addNumericStat(stats, budget.path("revenues"), "BUDGET_REVENUES", "Government Revenues", Category.ECONOMY, UNIT_USD, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        addNumericStat(stats, budget.path("expenditures"), "BUDGET_EXPENDITURES", "Government Expenditures", Category.ECONOMY, UNIT_USD, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        JsonNode publicDebtNode = econ.path("Public debt");
        if (!publicDebtNode.isMissingNode()) {
            publicDebtNode.fields().forEachRemaining(entry -> {
                if (!entry.getKey().equalsIgnoreCase("note")) {
                    addNumericStat(stats, entry.getValue(), "PUBLIC_DEBT",
                            "Public Debt (% of GDP)", Category.ECONOMY, UNIT_PCT_GDP,
                            BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
                }
            });
        }
        addNumericStat(stats, econ.path("Taxes and other revenues"), "TAX_REVENUE", "Tax Revenue (% of GDP)", Category.ECONOMY, UNIT_PERCENT, BigDecimal.valueOf(0.35), BASE_CONFIDENCE);

        JsonNode currentAccount = econ.path("Current account balance");
        addNumericStat(stats, currentAccount.path("Current account balance 2024"), "CURRENT_ACCOUNT_2024", "Current Account Balance 2024", Category.ECONOMY, UNIT_USD, BigDecimal.valueOf(0.40), HIGH_CONFIDENCE);

        JsonNode exports = econ.path("Exports");
        addNumericStat(stats, exports.path("Exports 2024"), "EXPORTS_2024", "Total Exports 2024", Category.ECONOMY, UNIT_USD, BigDecimal.valueOf(0.50), HIGH_CONFIDENCE);
        addTextStat(stats, econ.path("Exports - partners"), "EXPORT_PARTNERS", "Export Partners", Category.ECONOMY, BigDecimal.valueOf(0.65), BASE_CONFIDENCE);
        addTextStat(stats, econ.path("Exports - commodities"), "EXPORT_COMMODITIES", "Export Commodities", Category.ECONOMY, BigDecimal.valueOf(0.70), BASE_CONFIDENCE);

        JsonNode imports = econ.path("Imports");
        addNumericStat(stats, imports.path("Imports 2024"), "IMPORTS_2024", "Total Imports 2024", Category.ECONOMY, UNIT_USD, BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);
        addTextStat(stats, econ.path("Imports - partners"), "IMPORT_PARTNERS", "Import Partners", Category.ECONOMY, BigDecimal.valueOf(0.55), BASE_CONFIDENCE);
        addTextStat(stats, econ.path("Imports - commodities"), "IMPORT_COMMODITIES", "Import Commodities", Category.ECONOMY, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);

        JsonNode reserves = econ.path("Reserves of foreign exchange and gold");
        addNumericStat(stats, reserves.path("Reserves of foreign exchange and gold 2024"), "FOREX_RESERVES_2024", "Foreign Exchange Reserves 2024", Category.ECONOMY, UNIT_USD, BigDecimal.valueOf(0.45), HIGH_CONFIDENCE);

        JsonNode exchange = econ.path("Exchange rates");
        addTextStat(stats, exchange.path("Currency"), "CURRENCY", "Currency", Category.ECONOMY, BigDecimal.valueOf(0.85), HIGH_CONFIDENCE);

        return stats;
    }

    public List<RawStatistic> parseEnergy(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        JsonNode energy = root.path("Energy");
        if (energy.isMissingNode()) {
            return stats;
        }

        JsonNode electricity = energy.path("Electricity");
        addNumericStat(stats, electricity.path("installed generating capacity"), "ELECTRICITY_CAPACITY", "Electricity Generation Capacity", Category.INFRASTRUCTURE, "kW", BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        addNumericStat(stats, electricity.path("consumption"), "ELECTRICITY_CONSUMPTION", "Electricity Consumption", Category.INFRASTRUCTURE, "kWh", BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addNumericStat(stats, electricity.path("exports"), "ELECTRICITY_EXPORTS", "Electricity Exports", Category.INFRASTRUCTURE, "kWh", BigDecimal.valueOf(0.35), BASE_CONFIDENCE);
        addNumericStat(stats, electricity.path("imports"), "ELECTRICITY_IMPORTS", "Electricity Imports", Category.INFRASTRUCTURE, "kWh", BigDecimal.valueOf(0.35), BASE_CONFIDENCE);

        JsonNode elecSources = energy.path("Electricity generation sources");
        addNumericStat(stats, elecSources.path("fossil fuels"), "ELECTRICITY_FOSSIL_PCT", "Electricity from Fossil Fuels", Category.ENVIRONMENT, UNIT_PERCENT, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addNumericStat(stats, elecSources.path("nuclear"), "ELECTRICITY_NUCLEAR_PCT", "Electricity from Nuclear", Category.ENVIRONMENT, UNIT_PERCENT, BigDecimal.valueOf(0.55), BASE_CONFIDENCE);
        addNumericStat(stats, elecSources.path("solar"), "ELECTRICITY_SOLAR_PCT", "Electricity from Solar", Category.ENVIRONMENT, UNIT_PERCENT, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);
        addNumericStat(stats, elecSources.path("wind"), "ELECTRICITY_WIND_PCT", "Electricity from Wind", Category.ENVIRONMENT, UNIT_PERCENT, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);
        addNumericStat(stats, elecSources.path("hydroelectricity"), "ELECTRICITY_HYDRO_PCT", "Electricity from Hydro", Category.ENVIRONMENT, UNIT_PERCENT, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);

        JsonNode nuclear = energy.path("Nuclear energy");
        addNumericStat(stats, nuclear.path("Percent of total electricity production"), "NUCLEAR_ELECTRICITY_PCT", "Nuclear Electricity %", Category.ENVIRONMENT, UNIT_PERCENT, BigDecimal.valueOf(0.55), BASE_CONFIDENCE);
        addNumericStat(stats, nuclear.path("Number of nuclear reactors permanently shut down"), "NUCLEAR_REACTORS_SHUTDOWN", "Nuclear Reactors Shut Down", Category.ENVIRONMENT, "", BigDecimal.valueOf(0.50), BASE_CONFIDENCE);

        JsonNode coal = energy.path("Coal");
        addNumericStat(stats, coal.path("production"), "COAL_PRODUCTION", "Coal Production", Category.ECONOMY, "metric tons", BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addNumericStat(stats, coal.path("proven reserves"), "COAL_RESERVES", "Coal Reserves", Category.ECONOMY, "metric tons", BigDecimal.valueOf(0.40), BASE_CONFIDENCE);

        JsonNode petroleum = energy.path("Petroleum");
        addNumericStat(stats, petroleum.path("total petroleum production"), "OIL_PRODUCTION", "Oil Production", Category.ECONOMY, "bbl/day", BigDecimal.valueOf(0.55), BASE_CONFIDENCE);
        addNumericStat(stats, petroleum.path("refined petroleum consumption"), "OIL_CONSUMPTION", "Oil Consumption", Category.ECONOMY, "bbl/day", BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addNumericStat(stats, petroleum.path("crude oil estimated reserves"), "OIL_RESERVES", "Oil Reserves", Category.ECONOMY, "barrels", BigDecimal.valueOf(0.60), BASE_CONFIDENCE);

        JsonNode gas = energy.path("Natural gas");
        addNumericStat(stats, gas.path("production"), "GAS_PRODUCTION", "Natural Gas Production", Category.ECONOMY, UNIT_CUBIC_METERS, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);
        addNumericStat(stats, gas.path("consumption"), "GAS_CONSUMPTION", "Natural Gas Consumption", Category.ECONOMY, UNIT_CUBIC_METERS, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        addNumericStat(stats, gas.path("proven reserves"), "GAS_RESERVES", "Natural Gas Reserves", Category.ECONOMY, UNIT_CUBIC_METERS, BigDecimal.valueOf(0.55), BASE_CONFIDENCE);

        JsonNode co2 = energy.path("Carbon dioxide emissions");
        addNumericStat(stats, co2.path("total emissions"), "CO2_EMISSIONS_TOTAL", "CO2 Emissions (Total)", Category.ENVIRONMENT, "metric tonnes", BigDecimal.valueOf(0.50), BASE_CONFIDENCE);

        JsonNode energyPerCapita = energy.path("Energy consumption per capita");
        addNumericStat(stats, energyPerCapita.path("Total energy consumption per capita 2023"), "ENERGY_PER_CAPITA", "Energy Consumption per Capita", Category.ENVIRONMENT, "Btu/person", BigDecimal.valueOf(0.45), BASE_CONFIDENCE);

        return stats;
    }

    public List<RawStatistic> parseCommunications(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        JsonNode comms = root.path("Communications");
        if (comms.isMissingNode()) {
            return stats;
        }

        JsonNode fixedLines = comms.path("Telephones - fixed lines");
        addNumericStat(stats, fixedLines.path("total subscriptions"), "FIXED_PHONE_SUBSCRIPTIONS", "Fixed Phone Subscriptions", Category.INFRASTRUCTURE, "", BigDecimal.valueOf(0.35), BASE_CONFIDENCE);
        addNumericStat(stats, fixedLines.path("subscriptions per 100 inhabitants"), "FIXED_PHONE_PER_100", "Fixed Phones per 100 People", Category.INFRASTRUCTURE, "", BigDecimal.valueOf(0.40), BASE_CONFIDENCE);

        JsonNode mobile = comms.path("Telephones - mobile cellular");
        addNumericStat(stats, mobile.path("total subscriptions"), "MOBILE_SUBSCRIPTIONS", "Mobile Phone Subscriptions", Category.INFRASTRUCTURE, "", BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        addNumericStat(stats, mobile.path("subscriptions per 100 inhabitants"), "MOBILE_PER_100", "Mobile Phones per 100 People", Category.INFRASTRUCTURE, "", BigDecimal.valueOf(0.45), BASE_CONFIDENCE);

        JsonNode internet = comms.path("Internet users");
        addNumericStat(stats, internet.path("percent of population"), "INTERNET_PENETRATION", "Internet Users", Category.INFRASTRUCTURE, UNIT_PERCENT, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);

        JsonNode broadband = comms.path("Broadband - fixed subscriptions");
        addNumericStat(stats, broadband.path(FIELD_TOTAL), "BROADBAND_SUBSCRIPTIONS", "Broadband Subscriptions", Category.INFRASTRUCTURE, "", BigDecimal.valueOf(0.35), BASE_CONFIDENCE);

        return stats;
    }

    public List<RawStatistic> parseTransportation(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        JsonNode transport = root.path("Transportation");
        if (transport.isMissingNode()) {
            return stats;
        }

        addNumericStat(stats, transport.path("Airports"), "AIRPORTS_COUNT", "Number of Airports", Category.INFRASTRUCTURE, "", BigDecimal.valueOf(0.50), BASE_CONFIDENCE);
        addNumericStat(stats, transport.path("Heliports"), "HELIPORTS_COUNT", "Number of Heliports", Category.INFRASTRUCTURE, "", BigDecimal.valueOf(0.30), BASE_CONFIDENCE);

        JsonNode railways = transport.path("Railways");
        addNumericStat(stats, railways.path(FIELD_TOTAL), "RAILWAYS_TOTAL", "Total Railway Length", Category.INFRASTRUCTURE, UNIT_KM, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);

        JsonNode marine = transport.path("Merchant marine");
        addNumericStat(stats, marine.path(FIELD_TOTAL), "MERCHANT_SHIPS", "Merchant Ships", Category.INFRASTRUCTURE, "", BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addTextStat(stats, marine.path("by type"), "MERCHANT_SHIPS_TYPES", "Merchant Ship Types", Category.INFRASTRUCTURE, BigDecimal.valueOf(0.35), BASE_CONFIDENCE);

        JsonNode ports = transport.path("Ports");
        addNumericStat(stats, ports.path("total ports"), "PORTS_TOTAL", "Total Ports", Category.INFRASTRUCTURE, "", BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addTextStat(stats, ports.path("key ports"), "KEY_PORTS", "Key Ports", Category.INFRASTRUCTURE, BigDecimal.valueOf(0.70), BASE_CONFIDENCE);

        return stats;
    }

    public List<RawStatistic> parseMilitary(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        JsonNode military = root.path("Military and Security");
        if (military.isMissingNode()) {
            return stats;
        }

        JsonNode milExp = military.path("Military expenditures");
        addNumericStat(stats, milExp.path("Military Expenditures 2024"), "MILITARY_SPENDING_2024", "Military Spending 2024 (% GDP)", Category.ECONOMY, UNIT_PCT_GDP, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);
        addNumericStat(stats, milExp.path("Military Expenditures 2023"), "MILITARY_SPENDING_2023", "Military Spending 2023 (% GDP)", Category.ECONOMY, UNIT_PCT_GDP, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);
        addNumericStat(stats, military.path("Military and security service personnel strengths"), "MILITARY_PERSONNEL", "Military Personnel", Category.TRIVIA, UNIT_PEOPLE, BigDecimal.valueOf(0.50), BASE_CONFIDENCE);
        addTextStat(stats, military.path("Military service age and obligation"), "MILITARY_SERVICE_AGE", "Military Service Age", Category.TRIVIA, BigDecimal.valueOf(0.35), BASE_CONFIDENCE);

        return stats;
    }

    public List<RawStatistic> parseSpace(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        JsonNode space = root.path("Space");
        if (space.isMissingNode()) {
            return stats;
        }

        addTextStat(stats, space.path("Space launch site(s)"), "SPACE_LAUNCH_SITES", "Space Launch Sites", Category.TRIVIA, BigDecimal.valueOf(0.65), BASE_CONFIDENCE);
        addTextStat(stats, space.path("Space program overview"), "SPACE_PROGRAM", "Space Program", Category.TRIVIA, BigDecimal.valueOf(0.55), BASE_CONFIDENCE);
        addTextStat(stats, space.path("Key space-program milestones"), "SPACE_MILESTONES", "Space Program Milestones", Category.TRIVIA, BigDecimal.valueOf(0.60), BASE_CONFIDENCE);

        return stats;
    }

    public List<RawStatistic> parseTransnationalIssues(JsonNode root) {
        List<RawStatistic> stats = new ArrayList<>();
        JsonNode issues = root.path("Transnational Issues");
        if (issues.isMissingNode()) {
            return stats;
        }

        JsonNode refugees = issues.path("Refugees and internally displaced persons");
        addNumericStat(stats, refugees.path("refugees"), "REFUGEES_HOSTED", "Refugees Hosted", Category.DEMOGRAPHICS, UNIT_PEOPLE, BigDecimal.valueOf(0.65), BASE_CONFIDENCE);
        addNumericStat(stats, refugees.path("IDPs"), "INTERNALLY_DISPLACED", "Internally Displaced Persons", Category.DEMOGRAPHICS, UNIT_PEOPLE, BigDecimal.valueOf(0.55), BASE_CONFIDENCE);
        addNumericStat(stats, refugees.path("stateless persons"), "STATELESS_PERSONS", "Stateless Persons", Category.DEMOGRAPHICS, UNIT_PEOPLE, BigDecimal.valueOf(0.40), BASE_CONFIDENCE);
        addTextStat(stats, issues.path("Illicit drugs"), "ILLICIT_DRUGS", "Drug Trade Issues", Category.TRIVIA, BigDecimal.valueOf(0.45), BASE_CONFIDENCE);

        return stats;
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            return node.asText();
        }

        JsonNode textNode = node.path("text");
        if (!textNode.isMissingNode() && textNode.isTextual()) {
            return textNode.asText();
        }

        if (node.isObject()) {
            for (String fieldName : new String[]{"text", "Languages", FIELD_TOTAL, "name"}) {
                JsonNode child = node.path(fieldName);
                if (!child.isMissingNode()) {
                    String result = extractText(child);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        String text = node.toString();
        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text.isEmpty() ? null : text;
    }

    private Integer extractYear(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher matcher = YEAR_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal extractNumber(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher multiplierMatcher = MULTIPLIER_PATTERN.matcher(text);
        if (multiplierMatcher.find()) {
            try {
                double value = Double.parseDouble(multiplierMatcher.group(1));
                String multiplier = multiplierMatcher.group(2).toLowerCase();

                switch (multiplier) {
                    case "trillion":
                        value *= 1_000_000_000_000L;
                        break;
                    case "billion":
                        value *= 1_000_000_000L;
                        break;
                    case "million":
                        value *= 1_000_000L;
                        break;
                    case "thousand":
                        value *= 1_000L;
                        break;
                    default:
                        break;
                }

                return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                log.debug("Failed to parse number from '{}': {}", text, e.getMessage());
            }
        }

        text = text.replaceAll("\\s*\\([^)]*+\\)", "");

        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                String numStr = matcher.group(1).replace(",", "");
                return new BigDecimal(numStr).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String cleanText(String text) {
        if (text == null) return null;

        text = text.replaceAll("<br\\s*/?>", " ");
        text = text.replaceAll("<[^>]+>", "");
        text = StringEscapeUtils.unescapeHtml4(text);
        text = text.replace('\u00A0', ' ');
        text = text.replace("\\\"", "\"");
        text = text.replaceAll("\\s+", " ").trim();
        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }

        return text.isEmpty() ? null : text;
    }

    private void addNumericStat(List<RawStatistic> stats, JsonNode node,
                                String code, String name, Category category, String unit,
                                BigDecimal weight, BigDecimal confidence) {
        String text = extractText(node);
        if (!StringUtils.hasText(text)) {
            return;
        }

        BigDecimal value = extractNumber(text);
        Integer year = extractYear(text);

        if (value != null) {
            stats.add(new RawStatistic(
                    code, name, category,
                    value, null, unit, year,
                    confidence, weight
            ));
        }
    }

    private void addTextStat(List<RawStatistic> stats, JsonNode node,
                             String code, String name, Category category,
                             BigDecimal weight, BigDecimal confidence) {
        String text = extractText(node);
        text = cleanText(text);

        if (!StringUtils.hasText(text)) {
            return;
        }

        if (text.length() > 400) {
            log.debug("Skipping {} - text too long ({} chars)", code, text.length());
            return;
        }

        Integer year = extractYear(text);

        stats.add(new RawStatistic(
                code, name, category,
                null, text, null, year,
                confidence, weight
        ));
    }
}
