package io.clueterra.questions.clients.owid.hintgeneration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class OwidAmbiguityCalculator {

    private OwidAmbiguityCalculator() {
    }

    public static int byBracket(BigDecimal value, List<BigDecimal> allValues,
                                double bracketSize) {
        if (allValues == null || allValues.isEmpty()) return 0;
        double v = value.doubleValue();
        double low = Math.floor(v / bracketSize) * bracketSize;
        double high = low + bracketSize;
        int count = 0;
        for (BigDecimal other : allValues) {
            double o = other.doubleValue();
            if (o >= low && o < high) count++;
        }
        return Math.max(0, count - 1);
    }

    public static int byRoundedValue(BigDecimal value, List<BigDecimal> allValues, int scale) {
        if (allValues == null || allValues.isEmpty()) return 0;
        String target = value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
        int count = 0;
        for (BigDecimal other : allValues) {
            if (other.setScale(scale, RoundingMode.HALF_UP).toPlainString().equals(target)) count++;
        }
        return Math.max(0, count - 1);
    }

    public static int byRank(List<BigDecimal> allValues) {
        if (allValues == null) return 0;
        return Math.min(3, Math.max(0, (int) (allValues.size() * 0.08)));
    }
}
