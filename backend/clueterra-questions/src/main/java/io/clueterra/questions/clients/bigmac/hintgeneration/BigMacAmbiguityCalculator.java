package io.clueterra.questions.clients.bigmac.hintgeneration;

import io.clueterra.questions.clients.bigmac.BigMacConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class BigMacAmbiguityCalculator {

    private BigMacAmbiguityCalculator() {
    }

    public static int priceAmbiguity(BigDecimal price, List<BigDecimal> allPrices) {
        String priceStr = price.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return (int) allPrices.stream()
                .filter(p -> p.setScale(2, RoundingMode.HALF_UP).toPlainString().equals(priceStr))
                .count() - 1;
    }

    public static int rankAmbiguity(BigDecimal price, List<BigDecimal> allPrices) {
        return priceAmbiguity(price, allPrices);
    }

    public static int pppAmbiguity(BigDecimal valuation, List<BigDecimal> allValuations) {
        if (valuation == null || allValuations == null) return 0;
        String pctStr = valuation.abs().setScale(1, RoundingMode.HALF_UP).toPlainString();
        boolean isUnder = valuation.compareTo(BigDecimal.ZERO) < 0;
        return (int) allValuations.stream()
                .filter(v -> {
                    boolean vUnder = v.compareTo(BigDecimal.ZERO) < 0;
                    String vPct = v.abs().setScale(1, RoundingMode.HALF_UP).toPlainString();
                    return vUnder == isUnder && vPct.equals(pctStr);
                })
                .count() - 1;
    }

    public static int regionalOutlierAmbiguity(double deviationPct, List<BigDecimal> regionPrices,
                                               BigDecimal regionalAvg) {
        if (regionalAvg == null || regionalAvg.compareTo(BigDecimal.ZERO) == 0) return 0;
        int count = 0;
        for (BigDecimal p : regionPrices) {
            double dev = p.subtract(regionalAvg).doubleValue() / regionalAvg.doubleValue() * 100;
            if (Math.abs(dev - deviationPct) <= 5.0) {
                count++;
            }
        }
        return Math.max(0, count - 1);
    }

    public static int priceRatioAmbiguity(BigDecimal ratio, List<BigDecimal> allPrices,
                                          BigDecimal cheapestPrice) {
        if (cheapestPrice == null || cheapestPrice.compareTo(BigDecimal.ZERO) == 0) return 0;
        String targetRatio = ratio.setScale(1, RoundingMode.HALF_UP).toPlainString();
        int count = 0;
        for (BigDecimal p : allPrices) {
            BigDecimal r = p.divide(cheapestPrice, 4, RoundingMode.HALF_UP);
            if (r.setScale(1, RoundingMode.HALF_UP).toPlainString().equals(targetRatio)) {
                count++;
            }
        }
        return Math.max(0, count - 1);
    }

    public static int dollarParityAmbiguity(List<BigDecimal> allValuations) {
        if (allValuations == null) return 0;
        int count = 0;
        for (BigDecimal v : allValuations) {
            if (v.abs().doubleValue() <= BigMacConstants.DOLLAR_PARITY_THRESHOLD) {
                count++;
            }
        }
        return Math.max(0, count - 1);
    }

    public static int vsUsaAmbiguity(double diffPct, List<BigDecimal> allPrices, BigDecimal usPrice) {
        if (usPrice == null || usPrice.compareTo(BigDecimal.ZERO) == 0) return 0;
        int count = 0;
        for (BigDecimal p : allPrices) {
            double diff = p.subtract(usPrice).doubleValue() / usPrice.doubleValue() * 100;
            if (Math.abs(Math.abs(diff) - Math.abs(diffPct)) <= 5.0) {
                count++;
            }
        }
        return Math.max(0, count - 1);
    }
}
