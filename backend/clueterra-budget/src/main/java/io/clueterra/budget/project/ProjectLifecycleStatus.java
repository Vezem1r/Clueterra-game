package io.clueterra.budget.project;

public enum ProjectLifecycleStatus {
    UNKNOWN,
    PLANNED,
    UNDER_CONSTRUCTION,
    ON_HOLD,
    CANCELLED,
    ABANDONED,
    COMPLETED;

    public boolean isUnfinished() {
        return this != UNKNOWN && this != COMPLETED;
    }

    public boolean isActive() {
        return this == PLANNED || this == UNDER_CONSTRUCTION || this == ON_HOLD;
    }
}
