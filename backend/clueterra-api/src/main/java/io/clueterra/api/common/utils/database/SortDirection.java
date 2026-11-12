package io.clueterra.api.common.utils.database;

public final class SortDirection {

    public static final String ASC = "ASC";
    public static final String DESC = "DESC";

    private SortDirection() {
    }

    public static String resolve(String dir) {
        return ASC.equalsIgnoreCase(dir) ? ASC : DESC;
    }
}
