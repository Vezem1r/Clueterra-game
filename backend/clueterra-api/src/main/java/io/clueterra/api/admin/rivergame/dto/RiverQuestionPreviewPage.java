package io.clueterra.api.admin.rivergame.dto;

import java.util.List;

public record RiverQuestionPreviewPage(
        List<RiverQuestionPreviewDto> content,
        long totalElements,
        int page,
        int size) {
}
