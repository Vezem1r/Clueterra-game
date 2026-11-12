package io.clueterra.api.projectpages.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProjectPageDetailRow(
        String wikidataId,
        String slug,
        String costQuestion,
        String seoTitle,
        String metaDescription,
        String keywords,
        String intro,
        String answerDetail,
        String scopeText,
        String contextText,
        String methodologyText,
        String sourceName,
        String sourceUrl,
        Instant updatedAt,
        String name,
        String countryCode,
        String countryName,
        String category,
        Integer projectYear,
        BigDecimal answerCostUsd,
        BigDecimal sourceAmount,
        String sourceCurrency,
        Integer costYear,
        String costKind,
        String scopeLabel,
        String lifecycleStatus,
        String imageSourceSha1,
        String imageAltText,
        String imageAuthorName,
        String imageAttributionText,
        String imageLicenseLabel,
        String imageLicenseUrl,
        String imageSourcePageUrl,
        List<ProjectPageCostRow> costs,
        List<ProjectPageMetricRow> metrics,
        List<ProjectPageMilestoneRow> milestones,
        List<ProjectPageFactRow> facts,
        List<ProjectPageSourceRow> sources) {
}
