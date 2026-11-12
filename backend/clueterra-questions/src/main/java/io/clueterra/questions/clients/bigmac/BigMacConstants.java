package io.clueterra.questions.clients.bigmac;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BigMacConstants {

    private BigMacConstants() {
    }

    public static final String PRICE_INDICATOR = "BIG_MAC_PRICE_USD";
    public static final String VALUATION_INDICATOR = "BIG_MAC_VALUATION_PCT";
    public static final String VS_USA_INDICATOR = "BIGMAC_VS_USA";
    public static final String PRICE_DISPLAY_NAME = "Big Mac price";
    public static final String VALUATION_DISPLAY_NAME = "currency valuation (Big Mac Index)";

    public static final int TOP_BOTTOM_N = 5;
    public static final double CHEAP_PERCENTILE = 0.30;

    public static final double REGIONAL_OUTLIER_THRESHOLD = 0.25;
    public static final double PRICE_RATIO_THRESHOLD = 2.0;
    public static final double DOLLAR_PARITY_THRESHOLD = 5.0;
    public static final double VS_USA_THRESHOLD = 15.0;
    public static final int CURRENCY_EXTREME_N = 5;
    public static final int MIN_REGION_SIZE = 3;

    public static String comparisonText(BigDecimal price, BigDecimal globalAvg) {
        if (price == null || globalAvg == null || globalAvg.compareTo(BigDecimal.ZERO) == 0) {
            return "right in the middle of the pack";
        }
        double r = price.divide(globalAvg, 4, RoundingMode.HALF_UP).doubleValue();
        if (r >= 1.30) return "so you're in premium burger territory";
        if (r >= 1.10) return "a bit pricier than average, but nothing wild";
        if (r >= 0.90) return "right in the middle of the pack";
        if (r >= 0.70) return "cheaper than most — not a bad deal";
        return "a serious bargain compared to most countries";
    }

    public static String stretchText(BigDecimal valuationPct) {
        if (valuationPct == null) return "behaves unpredictably";
        return valuationPct.compareTo(BigDecimal.ZERO) < 0 ? "stretches further" : "doesn't go as far";
    }

    public static String moreLessText(BigDecimal valuationPct) {
        if (valuationPct == null) return "differently than";
        return valuationPct.compareTo(BigDecimal.ZERO) < 0 ? "less" : "more";
    }

    public static String outlierVibe(String direction) {
        return "above".equals(direction)
                ? "Your wallet might feel the burn."
                : "Burger bargain hunters, this is your spot.";
    }

    public static String currencyTakeaway(String direction) {
        return "over".equals(direction)
                ? "Things here cost more than the math says they should."
                : "Translation: your money buys more here than it looks on paper.";
    }

    public static String vsUsaQuip(boolean moreExpensive) {
        return moreExpensive
                ? "If you thought American burgers were pricey, wait until you see this bill."
                : "Your American dollars go further here — at least when it comes to burgers.";
    }
}
