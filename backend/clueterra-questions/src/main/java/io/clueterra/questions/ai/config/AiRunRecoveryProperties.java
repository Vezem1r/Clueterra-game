package io.clueterra.questions.ai.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiRunRecoveryProperties {

    private boolean autoCancelStalePendingRuns = true;
    private int stalePendingRunMinutes = 180;
}
