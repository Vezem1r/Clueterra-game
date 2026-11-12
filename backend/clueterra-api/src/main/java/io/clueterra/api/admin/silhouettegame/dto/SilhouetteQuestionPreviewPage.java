package io.clueterra.api.admin.silhouettegame.dto;

import java.util.List;

public record SilhouetteQuestionPreviewPage(
        List<SilhouetteQuestionPreviewDto> content,
        int page,
        int size,
        boolean hasNext,
        SilhouetteQuestionSummaryDto summary) {
}
