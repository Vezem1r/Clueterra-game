package io.clueterra.questions.service.sync;

import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import java.time.Instant;

public record StatisticWithSource(RawStatistic stat, DataSource source, Instant fetchedAt) {
}
