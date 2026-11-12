package io.clueterra.budget.source.exchange;

import java.time.Instant;
import java.util.Map;

public record ExchangeRateSnapshot(Instant fetchedAt, Map<String, ExchangeRateQuote> quotes) {
}
