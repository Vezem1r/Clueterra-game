package io.clueterra.questions.controller.dto;

import java.time.Instant;
import java.util.List;

public record SourcesOverview(int totalSources, int enabledSources, int healthySources,
                              List<SourceDetail> sources, Instant timestamp) {
}
