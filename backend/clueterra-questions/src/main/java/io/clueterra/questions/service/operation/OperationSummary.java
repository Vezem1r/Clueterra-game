package io.clueterra.questions.service.operation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OperationSummary {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public static OperationSummary empty() {
        return new OperationSummary();
    }

    public static OperationSummary of(String key, Object value) {
        return new OperationSummary().and(key, value);
    }

    public OperationSummary and(String key, Object value) {
        if (key != null && value != null) {
            values.put(key, value);
        }
        return this;
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }
}
