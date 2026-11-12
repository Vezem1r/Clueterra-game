package io.clueterra.api.admin.console.service;

public enum ConsoleChannel {
    QUESTIONS("questions"),
    BUDGET("budget");

    private static final String KEY_PREFIX = "admin:console:";

    private final String keySegment;

    ConsoleChannel(String keySegment) {
        this.keySegment = keySegment;
    }

    public String logsKey() {
        return KEY_PREFIX + keySegment + ":logs";
    }

    public String operationKey() {
        return KEY_PREFIX + keySegment + ":operation";
    }
}
