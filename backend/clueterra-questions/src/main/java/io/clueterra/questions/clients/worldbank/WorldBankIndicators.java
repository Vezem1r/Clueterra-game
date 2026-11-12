package io.clueterra.questions.clients.worldbank;

import io.clueterra.questions.entity.enums.Category;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WorldBankIndicators {

    private WorldBankIndicators() {
    }

    private static final String GDP_PERCENTAGE = "% of GDP";
    private static final String GROSS_PERCENTAGE = "% gross";
    private static final String ANNUAL_PERCENTAGE = "% annual";
    private static final String YEARS_UNIT = "years";

    private static final BigDecimal WEIGHT_CORE = BigDecimal.valueOf(0.95);
    private static final BigDecimal WEIGHT_HIGH = BigDecimal.valueOf(0.85);
    private static final BigDecimal WEIGHT_MEDIUM = BigDecimal.valueOf(0.70);
    private static final BigDecimal WEIGHT_LOW = BigDecimal.valueOf(0.55);
    private static final BigDecimal WEIGHT_VERY_LOW = BigDecimal.valueOf(0.40);

    static final Map<String, IndicatorConfig> ALL = createIndicatorConfigs();

    private static Map<String, IndicatorConfig> createIndicatorConfigs() {
        Map<String, IndicatorConfig> indicators = new LinkedHashMap<>();

        // Demographics
        indicators.put("SP.POP.TOTL", new IndicatorConfig("Total Population", Category.DEMOGRAPHICS, "people", WEIGHT_CORE));
        indicators.put("SP.DYN.LE00.IN", new IndicatorConfig("Life Expectancy", Category.HEALTH, YEARS_UNIT, WEIGHT_HIGH));
        indicators.put("SP.URB.TOTL.IN.ZS", new IndicatorConfig("Urban Population", Category.DEMOGRAPHICS, "%", WEIGHT_HIGH));
        indicators.put("SP.POP.GROW", new IndicatorConfig("Population Growth Rate", Category.DEMOGRAPHICS, ANNUAL_PERCENTAGE, WEIGHT_MEDIUM));
        indicators.put("SP.DYN.TFRT.IN", new IndicatorConfig("Fertility Rate", Category.DEMOGRAPHICS, "births per woman", WEIGHT_MEDIUM));
        indicators.put("SP.POP.0014.TO.ZS", new IndicatorConfig("Population 0-14 years", Category.DEMOGRAPHICS, "%", WEIGHT_MEDIUM));
        indicators.put("SP.POP.65UP.TO.ZS", new IndicatorConfig("Population 65+ years", Category.DEMOGRAPHICS, "%", WEIGHT_MEDIUM));
        indicators.put("SM.POP.NETM", new IndicatorConfig("Net Migration", Category.DEMOGRAPHICS, "per 1000 people", WEIGHT_LOW));
        indicators.put("SP.POP.MED", new IndicatorConfig("Median Age", Category.DEMOGRAPHICS, YEARS_UNIT, WEIGHT_HIGH));
        indicators.put("SP.POP.MALE.TO.FEM.TO", new IndicatorConfig("Sex Ratio (M/F)", Category.DEMOGRAPHICS, "ratio", WEIGHT_MEDIUM));
        indicators.put("SP.RUR.TOTL.ZS", new IndicatorConfig("Rural Population %", Category.DEMOGRAPHICS, "%", WEIGHT_HIGH));
        indicators.put("SP.DYN.AMRT.MA", new IndicatorConfig("Adult Mortality Male", Category.HEALTH, "per 1000", WEIGHT_LOW));
        indicators.put("SP.DYN.AMRT.FE", new IndicatorConfig("Adult Mortality Female", Category.HEALTH, "per 1000", WEIGHT_LOW));
        indicators.put("SM.POP.REFG.ZS", new IndicatorConfig("Refugee Population %", Category.DEMOGRAPHICS, "%", WEIGHT_LOW));
        indicators.put("SP.DYN.TO65.MA.ZS", new IndicatorConfig("Population 65+ Male %", Category.DEMOGRAPHICS, "%", WEIGHT_LOW));
        indicators.put("SP.DYN.TO65.FE.ZS", new IndicatorConfig("Population 65+ Female %", Category.DEMOGRAPHICS, "%", WEIGHT_LOW));
        indicators.put("SP.POP.GROW.RATE.FEMALE", new IndicatorConfig("Female Population Growth %", Category.DEMOGRAPHICS, "%", WEIGHT_LOW));
        indicators.put("SP.POP.GROW.RATE.MALE", new IndicatorConfig("Male Population Growth %", Category.DEMOGRAPHICS, "%", WEIGHT_LOW));

        // Economy
        indicators.put("NY.GDP.MKTP.CD", new IndicatorConfig("GDP (current US$)", Category.ECONOMY, "USD", WEIGHT_CORE));
        indicators.put("NY.GDP.PCAP.CD", new IndicatorConfig("GDP per Capita", Category.ECONOMY, "USD", WEIGHT_HIGH));
        indicators.put("NY.GDP.MKTP.KD.ZG", new IndicatorConfig("GDP Growth Rate", Category.ECONOMY, ANNUAL_PERCENTAGE, WEIGHT_HIGH));
        indicators.put("FP.CPI.TOTL.ZG", new IndicatorConfig("Inflation Rate", Category.ECONOMY, "%", WEIGHT_MEDIUM));
        indicators.put("SL.UEM.TOTL.ZS", new IndicatorConfig("Unemployment Rate", Category.ECONOMY, "%", WEIGHT_HIGH));
        indicators.put("SI.POV.GINI", new IndicatorConfig("Gini Coefficient", Category.ECONOMY, "index", WEIGHT_LOW));
        indicators.put("NE.EXP.GNFS.ZS", new IndicatorConfig("Exports of Goods and Services", Category.ECONOMY, GDP_PERCENTAGE, WEIGHT_MEDIUM));
        indicators.put("NE.IMP.GNFS.ZS", new IndicatorConfig("Imports of Goods and Services", Category.ECONOMY, GDP_PERCENTAGE, WEIGHT_MEDIUM));
        indicators.put("NV.AGR.TOTL.ZS", new IndicatorConfig("Agriculture GDP Share", Category.ECONOMY, GDP_PERCENTAGE, WEIGHT_MEDIUM));
        indicators.put("NV.IND.TOTL.ZS", new IndicatorConfig("Industry GDP Share", Category.ECONOMY, GDP_PERCENTAGE, WEIGHT_MEDIUM));
        indicators.put("NV.SRV.TOTL.ZS", new IndicatorConfig("Services GDP Share", Category.ECONOMY, GDP_PERCENTAGE, WEIGHT_MEDIUM));
        indicators.put("GC.DOD.TOTL.GD.ZS", new IndicatorConfig("Government Debt to GDP", Category.ECONOMY, "%", WEIGHT_MEDIUM));
        indicators.put("DT.DOD.DECT.PC", new IndicatorConfig("External Debt per Capita", Category.ECONOMY, "USD", WEIGHT_LOW));
        indicators.put("FI.RES.TOTL.CD", new IndicatorConfig("Foreign Exchange Reserves", Category.ECONOMY, "USD", WEIGHT_LOW));
        indicators.put("ST.INT.RCPT.CD", new IndicatorConfig("Tourism Receipts", Category.ECONOMY, "USD", WEIGHT_LOW));
        indicators.put("BX.TRF.PWKR.CD.DT", new IndicatorConfig("Remittances Received", Category.ECONOMY, "USD", WEIGHT_LOW));
        indicators.put("NY.GDP.TOTL.RT.ZS", new IndicatorConfig("Natural Resource Rents", Category.ECONOMY, GDP_PERCENTAGE, WEIGHT_LOW));
        indicators.put("SI.DST.10TH.10", new IndicatorConfig("Income Share of Top 10%", Category.ECONOMY, "%", WEIGHT_VERY_LOW));
        indicators.put("NY.GDP.FCST.KD.ZG", new IndicatorConfig("Forecasted GDP Growth", Category.ECONOMY, ANNUAL_PERCENTAGE, WEIGHT_MEDIUM));
        indicators.put("NE.TRD.GNFS.ZS", new IndicatorConfig("Trade (% of GDP)", Category.ECONOMY, GDP_PERCENTAGE, WEIGHT_HIGH));
        indicators.put("FI.RES.TOTL.GDP.ZS", new IndicatorConfig("Reserves (% of GDP)", Category.ECONOMY, GDP_PERCENTAGE, WEIGHT_LOW));
        indicators.put("GC.BAL.CASH.GD.ZS", new IndicatorConfig("Cash Surplus/Deficit % of GDP", Category.ECONOMY, GDP_PERCENTAGE, WEIGHT_LOW));
        indicators.put("MS.MIL.XPND.PC", new IndicatorConfig("Military Expenditure per Capita", Category.ECONOMY, "USD", WEIGHT_LOW));

        // Geography
        indicators.put("AG.LND.TOTL.K2", new IndicatorConfig("Land Area", Category.GEOGRAPHY, "km²", WEIGHT_CORE));
        indicators.put("AG.LND.FRST.ZS", new IndicatorConfig("Forest Area", Category.GEOGRAPHY, "% of land", WEIGHT_MEDIUM));
        indicators.put("AG.LND.AGRI.ZS", new IndicatorConfig("Agricultural Land", Category.GEOGRAPHY, "% of land", WEIGHT_MEDIUM));
        indicators.put("EN.POP.DNST", new IndicatorConfig("Population Density", Category.ENVIRONMENT, "people/km²", WEIGHT_HIGH));

        // Infrastructure
        indicators.put("IT.NET.USER.ZS", new IndicatorConfig("Internet Users", Category.INFRASTRUCTURE, "% of population", WEIGHT_MEDIUM));
        indicators.put("IT.CEL.SETS.P2", new IndicatorConfig("Mobile Cellular Subscriptions", Category.INFRASTRUCTURE, "per 100 people", WEIGHT_HIGH));
        indicators.put("EG.ELC.ACCS.ZS", new IndicatorConfig("Access to Electricity", Category.INFRASTRUCTURE, "% of population", WEIGHT_MEDIUM));
        indicators.put("IS.VEH.NVEH.P3", new IndicatorConfig("Vehicles per 1000 people", Category.INFRASTRUCTURE, "vehicles/1000 people", WEIGHT_MEDIUM));
        indicators.put("IT.NET.BBND.P2", new IndicatorConfig("Broadband Subscriptions", Category.INFRASTRUCTURE, "per 100 people", WEIGHT_VERY_LOW));
        indicators.put("FB.CBK.ACCS.ZS", new IndicatorConfig("Mobile Money Accounts %", Category.INFRASTRUCTURE, "% of adults", WEIGHT_VERY_LOW));
        indicators.put("IS.VEH.ELEC.PC", new IndicatorConfig("Electric Vehicles per Capita", Category.INFRASTRUCTURE, "vehicles/1000 people", WEIGHT_LOW));
        indicators.put("IS.ROD.DNST", new IndicatorConfig("Road Density", Category.INFRASTRUCTURE, "km/km²", WEIGHT_MEDIUM));
        indicators.put("IT.NET.USER.RUR.ZS", new IndicatorConfig("Rural Internet Access %", Category.INFRASTRUCTURE, "%", WEIGHT_LOW));

        // Education
        indicators.put("SE.XPD.TOTL.GD.ZS", new IndicatorConfig("Education Expenditure", Category.EDUCATION, GDP_PERCENTAGE, WEIGHT_LOW));
        indicators.put("SE.ADT.LITR.ZS", new IndicatorConfig("Adult Literacy Rate", Category.EDUCATION, "%", WEIGHT_HIGH));
        indicators.put("SE.PRM.ENRR", new IndicatorConfig("Primary School Enrollment", Category.EDUCATION, GROSS_PERCENTAGE, WEIGHT_HIGH));
        indicators.put("SE.SEC.ENRR", new IndicatorConfig("Secondary School Enrollment", Category.EDUCATION, GROSS_PERCENTAGE, WEIGHT_HIGH));
        indicators.put("SE.TER.ENRR", new IndicatorConfig("Tertiary School Enrollment", Category.EDUCATION, GROSS_PERCENTAGE, WEIGHT_MEDIUM));
        indicators.put("SE.ADT.LITR.FE.ZS", new IndicatorConfig("Female Literacy Rate", Category.EDUCATION, "%", WEIGHT_HIGH));
        indicators.put("SE.ADT.LITR.MA.ZS", new IndicatorConfig("Male Literacy Rate", Category.EDUCATION, "%", WEIGHT_HIGH));
        indicators.put("SE.TER.MENR.FE.ZS", new IndicatorConfig("Tertiary Enrollment Female", Category.EDUCATION, GROSS_PERCENTAGE, WEIGHT_MEDIUM));
        indicators.put("SE.TER.MENR.MA.ZS", new IndicatorConfig("Tertiary Enrollment Male", Category.EDUCATION, GROSS_PERCENTAGE, WEIGHT_MEDIUM));
        indicators.put("SE.PRM.PRIV.ZS", new IndicatorConfig("Private Primary Enrollment %", Category.EDUCATION, "%", WEIGHT_LOW));

        // Health
        indicators.put("SH.XPD.CHEX.GD.ZS", new IndicatorConfig("Health Expenditure", Category.HEALTH, GDP_PERCENTAGE, WEIGHT_LOW));
        indicators.put("SH.DYN.MORT", new IndicatorConfig("Infant Mortality Rate", Category.HEALTH, "per 1,000 live births", WEIGHT_MEDIUM));
        indicators.put("SH.MED.PHYS.ZS", new IndicatorConfig("Physicians per 1000", Category.HEALTH, "per 1,000 people", WEIGHT_MEDIUM));
        indicators.put("SH.STA.BRTC.ZS", new IndicatorConfig("Births Attended by Skilled Staff", Category.HEALTH, "%", WEIGHT_MEDIUM));
        indicators.put("SH.H2O.SAFE.ZS", new IndicatorConfig("Access to Safe Water", Category.HEALTH, "%", WEIGHT_MEDIUM));
        indicators.put("SH.STA.OB18.ZS", new IndicatorConfig("Adult Obesity Rate", Category.HEALTH, "%", WEIGHT_MEDIUM));
        indicators.put("SH.PRV.SMOK.MA.ZS", new IndicatorConfig("Smoking Male %", Category.HEALTH, "%", WEIGHT_MEDIUM));
        indicators.put("SH.PRV.SMOK.FE.ZS", new IndicatorConfig("Smoking Female %", Category.HEALTH, "%", WEIGHT_MEDIUM));
        indicators.put("SH.DYN.AIDS.ZS", new IndicatorConfig("HIV Prevalence %", Category.HEALTH, "%", WEIGHT_MEDIUM));
        indicators.put("SH.ALC.PCAP.LI", new IndicatorConfig("Alcohol Consumption per Capita", Category.HEALTH, "liters", WEIGHT_LOW));
        indicators.put("SH.STA.OBCD.ZS", new IndicatorConfig("Child Obesity %", Category.HEALTH, "%", WEIGHT_MEDIUM));
        indicators.put("SP.DYN.LE00.FE", new IndicatorConfig("Female Life Expectancy", Category.HEALTH, YEARS_UNIT, WEIGHT_HIGH));
        indicators.put("SP.DYN.LE00.MA", new IndicatorConfig("Male Life Expectancy", Category.HEALTH, YEARS_UNIT, WEIGHT_HIGH));
        indicators.put("SH.STA.DISR.ZS", new IndicatorConfig("Mortality due to Disasters", Category.HEALTH, "per 100,000", WEIGHT_VERY_LOW));

        // Environment
        indicators.put("EN.ATM.CO2E.PC", new IndicatorConfig("CO2 Emissions per Capita", Category.ENVIRONMENT, "metric tons", WEIGHT_HIGH));
        indicators.put("EN.ATM.CO2E.KT", new IndicatorConfig("Total CO2 Emissions", Category.ENVIRONMENT, "kt", WEIGHT_MEDIUM));
        indicators.put("ER.H2O.FWTL.ZS", new IndicatorConfig("Freshwater Withdrawal", Category.ENVIRONMENT, "% of resources", WEIGHT_MEDIUM));
        indicators.put("EG.USE.ELEC.KH.PC", new IndicatorConfig("Electric Power Consumption", Category.ENVIRONMENT, "kWh per capita", WEIGHT_MEDIUM));
        indicators.put("EG.FEC.RNEW.ZS", new IndicatorConfig("Renewable Energy Share", Category.ENVIRONMENT, "%", WEIGHT_MEDIUM));
        indicators.put("AG.LND.TOTL.KM", new IndicatorConfig("Total Land Km²", Category.GEOGRAPHY, "km²", WEIGHT_HIGH));
        indicators.put("AG.LND.LABR.ZS", new IndicatorConfig("Land Labor Productivity", Category.ENVIRONMENT, "USD/ha", WEIGHT_LOW));
        indicators.put("EG.ELC.RNEW.ZS", new IndicatorConfig("Renewable Electricity %", Category.ENVIRONMENT, "%", WEIGHT_VERY_LOW));
        indicators.put("EN.ATM.PM25.MC.M3", new IndicatorConfig("PM2.5 Air Pollution", Category.ENVIRONMENT, "µg/m³", WEIGHT_VERY_LOW));
        indicators.put("EN.CLC.COAL.ZS", new IndicatorConfig("Coal Consumption %", Category.ENVIRONMENT, "%", WEIGHT_VERY_LOW));
        indicators.put("EG.USE.COMM.KH.PC", new IndicatorConfig("Commercial Energy Use per Capita", Category.ENVIRONMENT, "kWh per capita", WEIGHT_VERY_LOW));
        indicators.put("EN.ATM.CO2E.LF.ZS", new IndicatorConfig("CO2 Emissions from Land Use", Category.ENVIRONMENT, "%", WEIGHT_VERY_LOW));
        indicators.put("ER.H2O.PCAP.ZS", new IndicatorConfig("Water Use per Capita", Category.ENVIRONMENT, "%", WEIGHT_VERY_LOW));

        // Security / Politics
        indicators.put("MS.MIL.XPND.GD.ZS", new IndicatorConfig("Military Expenditure", Category.ECONOMY, GDP_PERCENTAGE, WEIGHT_HIGH));

        return indicators;
    }
}
