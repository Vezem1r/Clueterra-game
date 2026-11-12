package io.clueterra.api.admin.indicators.dto;

import java.util.List;

public record IndicatorListCache(
        List<IndicatorConfigAdminDto> content,
        long totalElements) {
}
