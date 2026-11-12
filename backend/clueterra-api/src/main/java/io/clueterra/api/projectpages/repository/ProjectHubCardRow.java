package io.clueterra.api.projectpages.repository;

import java.math.BigDecimal;

public record ProjectHubCardRow(
        String wikidataId,
        String slug,
        String name,
        String countryName,
        String category,
        Integer projectYear,
        BigDecimal answerCostUsd,
        BigDecimal sourceAmount,
        String sourceCurrency,
        String costKind,
        String scopeLabel,
        String lifecycleStatus,
        String rankingScope,
        String imageSourceSha1,
        String imageAltText) {
}
