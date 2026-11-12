package io.clueterra.api.projectpages.repository;

import java.math.BigDecimal;

public record ProjectPageCostRow(
        String costKind,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal amountUsd,
        Integer costYear,
        boolean featured,
        String scopeLabel,
        String sourceName,
        String sourceUrl) {
}
