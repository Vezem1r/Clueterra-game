package io.clueterra.budget.project;

public enum ProjectMetricType {
    LENGTH("M"),
    MAIN_SPAN("M"),
    HEIGHT("M"),
    FLOOR_AREA("M2"),
    CAPACITY("SEAT"),
    POWER("MW"),
    ANNUAL_PASSENGERS("PASSENGERS_PER_YEAR"),
    ANNUAL_CARGO_TONNES("TONNES_PER_YEAR"),
    CONTAINER_CAPACITY("TEU_PER_YEAR"),
    STORAGE_VOLUME("M3"),
    MASS("KG");

    private final String normalizedUnit;

    ProjectMetricType(String normalizedUnit) {
        this.normalizedUnit = normalizedUnit;
    }

    public String normalizedUnit() {
        return normalizedUnit;
    }
}
