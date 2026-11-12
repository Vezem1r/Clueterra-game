package io.clueterra.api.admin.hints.dto;

import java.util.List;

public record HintListCache(List<HintAdminDto> content, long totalElements) {
}
