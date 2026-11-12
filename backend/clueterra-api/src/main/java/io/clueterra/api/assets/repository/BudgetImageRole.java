package io.clueterra.api.assets.repository;

import java.util.Locale;

public enum BudgetImageRole {
    PRIMARY,
    COMPARISON;

    public static BudgetImageRole fromPath(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Budget image role is required");
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported Budget image role");
        }
    }
}
