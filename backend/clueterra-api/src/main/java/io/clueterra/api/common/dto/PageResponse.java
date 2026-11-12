package io.clueterra.api.common.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size) {
}
