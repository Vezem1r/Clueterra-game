package io.clueterra.budget.source.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateQuote(
        BigDecimal unitsPerUsd, LocalDate rateDate, ExchangeRateBasis basis) {
}
