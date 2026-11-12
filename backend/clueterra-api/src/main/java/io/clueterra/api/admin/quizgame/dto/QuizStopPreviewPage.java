package io.clueterra.api.admin.quizgame.dto;

import java.util.List;

public record QuizStopPreviewPage(
        List<QuizStopPreviewDto> content,
        int page,
        int size,
        boolean hasNext,
        QuizContentSummaryDto summary) {
}
