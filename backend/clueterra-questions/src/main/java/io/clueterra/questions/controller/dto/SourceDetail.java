package io.clueterra.questions.controller.dto;

import java.util.Map;

public record SourceDetail(String name, String displayName, String baseUrl, int priority, boolean enabled,
                           boolean healthy, String status, boolean syncAvailable,
                           boolean hintGenerationAvailable, Map<String, Object> metadata, String message) {
}
