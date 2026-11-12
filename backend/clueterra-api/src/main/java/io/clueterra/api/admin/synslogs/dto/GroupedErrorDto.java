package io.clueterra.api.admin.synslogs.dto;

import java.util.List;

public record GroupedErrorDto(
        String errorMessage,
        int count,
        String firstOccurrence,
        String lastOccurrence,
        List<String> affectedCountries) {
}
