package io.clueterra.questions.clients.worldbank;

import io.clueterra.questions.clients.common.dto.RawStatistic;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public record IndicatorFetch(String indicatorCode, CompletableFuture<Optional<RawStatistic>> future) {
}
