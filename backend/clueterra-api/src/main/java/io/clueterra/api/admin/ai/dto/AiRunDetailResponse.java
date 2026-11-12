package io.clueterra.api.admin.ai.dto;

import java.util.List;

public record AiRunDetailResponse(
        AiRunDto run,
        List<AiRunStageDto> stages,
        AiChangeSetDto changeSet,
        List<AiChangeItemDto> changeItems) {
}
