package io.clueterra.api.projectpages.service;

import java.time.LocalDate;
import java.util.List;

public record ProjectDetailView(
        String wikidataId,
        String slug,
        String seoTitle,
        String metaDescription,
        String keywords,
        String name,
        String costQuestion,
        String country,
        String category,
        String categoryHubPath,
        String countryHubPath,
        Integer year,
        String answer,
        String usdEquivalent,
        Integer costYear,
        String costType,
        String costScope,
        String status,
        String intro,
        String answerDetail,
        String scopeText,
        String contextText,
        String methodologyText,
        String sourceName,
        String sourceUrl,
        ProjectImageView image,
        List<ProjectCostView> costs,
        List<ProjectMetricView> metrics,
        List<ProjectMilestoneView> timeline,
        List<ProjectFactView> facts,
        List<ProjectSourceView> sources,
        LocalDate updatedDate) {
}
