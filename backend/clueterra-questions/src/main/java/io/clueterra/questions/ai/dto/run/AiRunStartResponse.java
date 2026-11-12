package io.clueterra.questions.ai.dto.run;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AiRunStartResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long runId,
        String runType,
        String status,
        int preparedStages,
        int preparedItems,
        long estimatedInputTokens,
        String message) {
}
