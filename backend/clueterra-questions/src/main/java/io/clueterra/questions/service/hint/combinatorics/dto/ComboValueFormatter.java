package io.clueterra.questions.service.hint.combinatorics.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public enum ComboValueFormatter {
    AUTO,
    INTEGER,
    DECIMAL_1,
    DECIMAL_2,
    PERCENT_1;

    public String format(BigDecimal value) {
        if (value == null) {
            return "";
        }

        return switch (this) {
            case AUTO -> formatAuto(value);
            case INTEGER -> formatFixed(value, 0, false);
            case DECIMAL_1 -> formatFixed(value, 1, false);
            case DECIMAL_2 -> formatFixed(value, 2, false);
            case PERCENT_1 -> formatFixed(value, 1, true);
        };
    }

    private String formatAuto(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return formatFixed(value, 0, false);
        }
        return formatFixed(value, 1, false);
    }

    private String formatFixed(BigDecimal value, int scale, boolean percent) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ENGLISH);
        DecimalFormat decimalFormat = new DecimalFormat(patternFor(scale), symbols);
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
        String formatted = decimalFormat.format(value.setScale(scale, RoundingMode.HALF_UP));
        return percent ? formatted + "%" : formatted;
    }

    private String patternFor(int scale) {
        if (scale <= 0) {
            return "#,##0";
        }
        return "#,##0." + "0".repeat(scale);
    }
}
