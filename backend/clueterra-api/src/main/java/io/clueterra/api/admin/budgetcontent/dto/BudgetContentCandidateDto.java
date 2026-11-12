package io.clueterra.api.admin.budgetcontent.dto;

import java.time.Instant;
import java.util.List;

public record BudgetContentCandidateDto(
        String wikidataId,
        String category,
        String name,
        String description,
        String countryCode,
        String countryName,
        String regionCode,
        Integer projectYear,
        String difficulty,
        int sitelinkCount,
        int monthlyPageviews,
        int interestScore,
        int qualityScore,
        int sourceRank,
        String qualityStatus,
        String evidenceTier,
        int costConfidenceScore,
        List<String> qualityFlags,
        String sourceUrl,
        String reviewStatus,
        String reviewNote,
        String reviewedBy,
        Instant reviewedAt,
        Instant fetchedAt,
        int duplicateNameCount,
        int duplicateImageCount,
        List<BudgetContentIssue> issues,
        BudgetContentImageDto image,
        BudgetContentCostDto cost,
        BudgetContentDescriptionSourceInput descriptionSource,
        List<BudgetContentCostClaimInput> costClaims,
        List<BudgetContentMetricInput> metrics,
        List<BudgetContentMilestoneInput> milestones) {
}
