package io.clueterra.questions.clients.budgetprojects;

import java.util.Arrays;
import java.util.Optional;

public enum BudgetProjectIndicator {
    PROJECT("PROJECT", null, null),
    CATEGORY("CATEGORY", null, null),
    DIFFICULTY("DIFFICULTY", null, null),
    LIFECYCLE("LIFECYCLE", null, null),
    OPENING_YEAR("OPENING_YEAR", null, null),
    COST_USD("COST_USD", null, "USD"),
    LENGTH("LENGTH", "LENGTH", "M"),
    MAIN_SPAN("MAIN_SPAN", "MAIN_SPAN", "M"),
    HEIGHT("HEIGHT", "HEIGHT", "M"),
    CAPACITY("CAPACITY", "CAPACITY", "SEAT"),
    POWER("POWER", "POWER", "MW"),
    ANNUAL_PASSENGERS("ANNUAL_PASSENGERS", "ANNUAL_PASSENGERS", "PASSENGERS_PER_YEAR"),
    CONTAINER_CAPACITY("CONTAINER_CAPACITY", "CONTAINER_CAPACITY", "TEU_PER_YEAR"),
    ANNUAL_CARGO_TONNES("ANNUAL_CARGO_TONNES", "ANNUAL_CARGO_TONNES", "TONNES_PER_YEAR"),
    FLOOR_AREA("FLOOR_AREA", "FLOOR_AREA", "M2"),
    STORAGE_VOLUME("STORAGE_VOLUME", "STORAGE_VOLUME", "M3"),
    MASS("MASS", "MASS", "KG");

    private final String suffix;
    private final String metricType;
    private final String unit;

    BudgetProjectIndicator(String suffix, String metricType, String unit) {
        this.suffix = suffix;
        this.metricType = metricType;
        this.unit = unit;
    }

    public String code(String wikidataId) {
        return "BUDGET_PROJECT_" + wikidataId + ":" + suffix;
    }

    public String unit() {
        return unit;
    }

    public String metricType() {
        return metricType;
    }

    public static Optional<BudgetProjectIndicator> forMetric(String metricType, String unit) {
        return Arrays.stream(values())
                .filter(indicator -> indicator.metricType != null)
                .filter(indicator -> indicator.metricType.equals(metricType) && indicator.unit.equals(unit))
                .findFirst();
    }
}
