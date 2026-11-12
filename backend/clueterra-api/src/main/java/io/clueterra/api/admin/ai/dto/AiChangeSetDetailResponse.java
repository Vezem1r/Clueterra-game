package io.clueterra.api.admin.ai.dto;

import java.util.List;

public record AiChangeSetDetailResponse(
        AiChangeSetDto changeSet,
        List<AiChangeItemDto> items,
        List<AiProposalDto> proposals) {
}
