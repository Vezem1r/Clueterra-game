package io.clueterra.api.admin.flaggame.dto;

import java.util.List;

public record FlagQuestionPreviewPage(
        List<FlagQuestionPreviewDto> content,
        int page,
        int size,
        boolean hasNext,
        FlagQuestionSummaryDto summary) {
}
