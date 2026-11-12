package io.clueterra.api.admin.analytics.dto;

public record SessionsByLevelDto(
        String level,
        long total,
        long completed) {
}
