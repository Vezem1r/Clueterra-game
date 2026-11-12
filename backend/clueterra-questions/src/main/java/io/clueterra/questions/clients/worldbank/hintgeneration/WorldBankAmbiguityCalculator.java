package io.clueterra.questions.clients.worldbank.hintgeneration;

import io.clueterra.questions.entity.CountryStatistic;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class WorldBankAmbiguityCalculator {

    public int computePopulationGiant(int rank) {
        return rankBandCount(rank);
    }

    public int computeGdpHeavyweight(int rank) {
        return rankBandCount(rank);
    }

    public int computeGdpPerCapita(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinRelativeRange("NY.GDP.PCAP.CD", value, 0.20, byCountry, countryId);
    }

    public int computeLifeExpectancy(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SP.DYN.LE00.IN", value, 1.5, byCountry, countryId);
    }

    public int computeDensity(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinRelativeRange("EN.POP.DNST", value, 0.25, byCountry, countryId);
    }

    public int computeInternet(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("IT.NET.USER.ZS", value, 3.0, byCountry, countryId);
    }

    public int computeRenewable(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("EG.FEC.RNEW.ZS", value, 10.0, byCountry, countryId);
    }

    public int computeTrade(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("NE.TRD.GNFS.ZS", value, 15.0, byCountry, countryId);
    }

    public int computeService(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("NV.SRV.TOTL.ZS", value, 5.0, byCountry, countryId);
    }

    public int computeAgriculture(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("NV.AGR.TOTL.ZS", value, 5.0, byCountry, countryId);
    }

    public int computeMedianAge(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SP.POP.MED", value, 2.0, byCountry, countryId);
    }

    public int computeMilitary(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("MS.MIL.XPND.GD.ZS", value, 1.0, byCountry, countryId);
    }

    public int computeUrbanization(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SP.URB.TOTL.IN.ZS", value, 5.0, byCountry, countryId);
    }

    public int computeFertility(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SP.DYN.TFRT.IN", value, 0.5, byCountry, countryId);
    }

    public int computeForest(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("AG.LND.FRST.ZS", value, 10.0, byCountry, countryId);
    }

    public int computeElectricity(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("EG.ELC.ACCS.ZS", value, 10.0, byCountry, countryId);
    }

    public int computeMobile(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinRelativeRange("IT.CEL.SETS.P2", value, 0.20, byCountry, countryId);
    }

    public int computePopGrowth(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SP.POP.GROW", value, 0.5, byCountry, countryId);
    }

    public int computeYouthBulge(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SP.POP.0014.TO.ZS", value, 5.0, byCountry, countryId);
    }

    public int computeElderly(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SP.POP.65UP.TO.ZS", value, 3.0, byCountry, countryId);
    }

    public int computeNetMigration(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinRelativeRange("SM.POP.NETM", value, 0.30, byCountry, countryId);
    }

    public int computeGdpGrowth(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("NY.GDP.MKTP.KD.ZG", value, 2.0, byCountry, countryId);
    }

    public int computeInflation(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("FP.CPI.TOTL.ZG", value, 5.0, byCountry, countryId);
    }

    public int computeUnemployment(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SL.UEM.TOTL.ZS", value, 3.0, byCountry, countryId);
    }

    public int computeDebt(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("GC.DOD.TOTL.GD.ZS", value, 15.0, byCountry, countryId);
    }

    public int computeGini(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SI.POV.GINI", value, 3.0, byCountry, countryId);
    }

    public int computeIndustry(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("NV.IND.TOTL.ZS", value, 5.0, byCountry, countryId);
    }

    public int computeResourceRents(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("NY.GDP.TOTL.RT.ZS", value, 5.0, byCountry, countryId);
    }

    public int computeTourismGiant(int rank) {
        return rankBandCount(rank);
    }

    public int computeForexReserves(int rank) {
        return rankBandCount(rank);
    }

    public int computeRemittances(int rank) {
        return rankBandCount(rank);
    }

    public int computeInfantMortality(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SH.DYN.MORT", value, 10.0, byCountry, countryId);
    }

    public int computePhysicians(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SH.MED.PHYS.ZS", value, 1.0, byCountry, countryId);
    }

    public int computeAlcohol(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SH.ALC.PCAP.LI", value, 2.0, byCountry, countryId);
    }

    public int computeHealthSpending(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SH.XPD.CHEX.GD.ZS", value, 2.0, byCountry, countryId);
    }

    public int computeHiv(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SH.DYN.AIDS.ZS", value, 2.0, byCountry, countryId);
    }

    public int computeLiteracy(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SE.ADT.LITR.ZS", value, 5.0, byCountry, countryId);
    }

    public int computeEducationSpending(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SE.XPD.TOTL.GD.ZS", value, 1.5, byCountry, countryId);
    }

    public int computeTertiaryEnrollment(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("SE.TER.ENRR", value, 10.0, byCountry, countryId);
    }

    public int computeAirPollution(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("EN.ATM.PM25.MC.M3", value, 10.0, byCountry, countryId);
    }

    public int computeWaterStress(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("ER.H2O.FWTL.ZS", value, 15.0, byCountry, countryId);
    }

    public int computeRenewableElectricity(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("EG.ELC.RNEW.ZS", value, 10.0, byCountry, countryId);
    }

    public int computeBroadband(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("IT.NET.BBND.P2", value, 5.0, byCountry, countryId);
    }

    public int computeLandArea(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinRelativeRange("AG.LND.TOTL.K2", value, 0.25, byCountry, countryId);
    }

    public int computeLandAreaRank(int rank) {
        return rankBandCount(rank);
    }

    public int computeAgriLand(BigDecimal value, Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        return countWithinAbsoluteRange("AG.LND.AGRI.ZS", value, 10.0, byCountry, countryId);
    }

    public int computeCombo(int... componentAmbiguities) {
        int min = Integer.MAX_VALUE;
        for (int a : componentAmbiguities) {
            min = Math.min(min, a);
        }
        return min == Integer.MAX_VALUE ? 1 : min;
    }

    private int rankBandCount(int rank) {
        if (rank <= 3) return 3;
        if (rank <= 6) return 3;
        return 4;
    }

    private int countWithinRelativeRange(String indicatorCode, BigDecimal value,
                                         double tolerance,
                                         Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        double target = value.doubleValue();
        double low = target * (1.0 - tolerance);
        double high = target * (1.0 + tolerance);
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream().anyMatch(s ->
                        indicatorCode.equals(s.getIndicatorCode())
                                && s.getValue() != null
                                && s.getValue().doubleValue() >= low
                                && s.getValue().doubleValue() <= high))
                .count();
    }

    private int countWithinAbsoluteRange(String indicatorCode, BigDecimal value,
                                         double tolerance,
                                         Map<Long, List<CountryStatistic>> byCountry, Long countryId) {
        double target = value.doubleValue();
        double low = target - tolerance;
        double high = target + tolerance;
        return (int) byCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream().anyMatch(s ->
                        indicatorCode.equals(s.getIndicatorCode())
                                && s.getValue() != null
                                && s.getValue().doubleValue() >= low
                                && s.getValue().doubleValue() <= high))
                .count();
    }
}
