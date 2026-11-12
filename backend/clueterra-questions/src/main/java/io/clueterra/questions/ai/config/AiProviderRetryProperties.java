package io.clueterra.questions.ai.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AiProviderRetryProperties {

    private List<Long> backoffMillis = List.of(30_000L, 60_000L, 120_000L, 300_000L);
}
