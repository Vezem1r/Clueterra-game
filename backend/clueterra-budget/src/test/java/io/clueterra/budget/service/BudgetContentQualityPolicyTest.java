package io.clueterra.budget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.clueterra.budget.project.BudgetContentEvidence;
import io.clueterra.budget.project.BudgetQualityFlag;
import io.clueterra.budget.project.ContentQualityStatus;
import io.clueterra.budget.project.CostEvidenceTier;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectCostVerificationStatus;
import io.clueterra.budget.project.WikidataProjectCost;

class BudgetContentQualityPolicyTest {

    private static final String STRONG_DESCRIPTION =
            "A major infrastructure project with enough concrete context for a playable question.";
    private static final BudgetContentEvidence VERIFIED_EVIDENCE =
            new BudgetContentEvidence(false, false, false);

    private final BudgetContentQualityPolicy policy = new BudgetContentQualityPolicy();

    @Test
    @DisplayName("should require review for non-authoritative knowledge-base cost")
    void shouldRequireReviewForNonAuthoritativeKnowledgeBaseCost() {
        WikidataProjectCost cost = cost(
                "1000000000",
                "USD",
                2020,
                "P2130",
                "Wikidata capital cost",
                "https://example.com/reference",
                "Whole project",
                ProjectCostVerificationStatus.CORROBORATED);

        var assessment = policy.assess(
                MegaprojectCategory.BRIDGE, STRONG_DESCRIPTION, 80, cost, VERIFIED_EVIDENCE);

        assertThat(assessment.status()).isEqualTo(ContentQualityStatus.NEEDS_REVIEW);
        assertThat(assessment.evidenceTier()).isEqualTo(CostEvidenceTier.REFERENCED_KNOWLEDGE_BASE);
        assertThat(assessment.flags()).contains(BudgetQualityFlag.NON_AUTHORITATIVE_COST_SOURCE);
    }

    @Test
    @DisplayName("should classify NASA reports as official evidence")
    void shouldClassifyNasaReportsAsOfficialEvidence() {
        WikidataProjectCost cost = cost(
                "1000000000",
                "USD",
                2020,
                "NASA_OIG_IG_20_023",
                "NASA OIG report IG-20-023",
                "https://oig.nasa.gov/docs/IG-20-023.pdf",
                "NASA project life-cycle cost",
                ProjectCostVerificationStatus.SOURCE_BACKED);

        var assessment = policy.assess(
                MegaprojectCategory.SPACE, STRONG_DESCRIPTION, 80, cost, VERIFIED_EVIDENCE);

        assertThat(assessment.status()).isEqualTo(ContentQualityStatus.AUTO_APPROVED);
        assertThat(assessment.evidenceTier()).isEqualTo(CostEvidenceTier.OFFICIAL_SOURCE);
        assertThat(assessment.confidenceScore()).isEqualTo(98);
        assertThat(assessment.flags()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blockingQualitySignals")
    @DisplayName("should require review when a blocking quality signal is present")
    void shouldRequireReviewForBlockingQualitySignals(
            String scenario,
            MegaprojectCategory category,
            String description,
            int qualityScore,
            WikidataProjectCost cost,
            BudgetContentEvidence evidence,
            BudgetQualityFlag expectedFlag) {
        var assessment = policy.assess(category, description, qualityScore, cost, evidence);

        assertThat(assessment.status())
                .as(scenario)
                .isEqualTo(ContentQualityStatus.NEEDS_REVIEW);
        assertThat(assessment.flags()).contains(expectedFlag);
    }

    private static Stream<Arguments> blockingQualitySignals() {
        WikidataProjectCost baseline = cost(
                "1000000000",
                "USD",
                2020,
                "NASA_REPORT",
                "NASA report",
                "https://nasa.gov/reference",
                "Whole project",
                ProjectCostVerificationStatus.SOURCE_BACKED);
        return Stream.of(
                Arguments.of(
                        "weak description",
                        MegaprojectCategory.BRIDGE,
                        "bridge",
                        80,
                        baseline,
                        VERIFIED_EVIDENCE,
                        BudgetQualityFlag.WEAK_DESCRIPTION),
                Arguments.of(
                        "category cost outlier",
                        MegaprojectCategory.STADIUM,
                        STRONG_DESCRIPTION,
                        80,
                        cost(
                                "16000000000", "USD", 2020, "P2130",
                                "Wikidata capital cost", "https://example.com/reference",
                                "Whole project", ProjectCostVerificationStatus.SOURCE_BACKED),
                        VERIFIED_EVIDENCE,
                        BudgetQualityFlag.CATEGORY_COST_OUTLIER),
                Arguments.of(
                        "currency era mismatch",
                        MegaprojectCategory.BRIDGE,
                        STRONG_DESCRIPTION,
                        80,
                        cost(
                                "1000000000", "EUR", 1998, "P2130",
                                "Wikidata capital cost", "https://example.com/reference",
                                "Whole project", ProjectCostVerificationStatus.SOURCE_BACKED),
                        VERIFIED_EVIDENCE,
                        BudgetQualityFlag.CURRENCY_ERA_MISMATCH),
                Arguments.of(
                        "project-part amount",
                        MegaprojectCategory.BRIDGE,
                        STRONG_DESCRIPTION,
                        80,
                        cost(
                                "1000000000", "USD", 2020, "P2130",
                                "Wikidata capital cost", "https://example.com/reference",
                                "Applies to project part: east section",
                                ProjectCostVerificationStatus.SOURCE_BACKED),
                        VERIFIED_EVIDENCE,
                        BudgetQualityFlag.PROJECT_PART_COST),
                Arguments.of(
                        "lead-only amount",
                        MegaprojectCategory.BRIDGE,
                        STRONG_DESCRIPTION,
                        80,
                        cost(
                                "1000000000", "USD", 2020, "WIKIPEDIA_LEAD:cost",
                                "Wikipedia lead", "https://example.com/reference",
                                "Whole project", ProjectCostVerificationStatus.SOURCE_BACKED),
                        VERIFIED_EVIDENCE,
                        BudgetQualityFlag.LEAD_ONLY_COST),
                Arguments.of(
                        "low-authority encyclopedia snapshot",
                        MegaprojectCategory.BRIDGE,
                        STRONG_DESCRIPTION,
                        55,
                        cost(
                                "1000000000", "USD", 2020, "WIKIPEDIA_INFOBOX:cost",
                                "Wikipedia infobox snapshot", "https://example.com/reference",
                                "Whole project", ProjectCostVerificationStatus.SOURCE_BACKED),
                        VERIFIED_EVIDENCE,
                        BudgetQualityFlag.LOW_AUTHORITY_COST),
                Arguments.of(
                        "disputed amount",
                        MegaprojectCategory.BRIDGE,
                        STRONG_DESCRIPTION,
                        80,
                        cost(
                                "1000000000", "USD", 2020, "NASA_REPORT",
                                "NASA report", "https://nasa.gov/reference",
                                "Whole project", ProjectCostVerificationStatus.DISPUTED),
                        VERIFIED_EVIDENCE,
                        BudgetQualityFlag.COST_SOURCE_CONFLICT),
                Arguments.of(
                        "unverified project metadata",
                        MegaprojectCategory.BRIDGE,
                        STRONG_DESCRIPTION,
                        80,
                        baseline,
                        new BudgetContentEvidence(true, false, false),
                        BudgetQualityFlag.UNVERIFIED_PROJECT_METADATA),
                Arguments.of(
                        "unverified project metric",
                        MegaprojectCategory.BRIDGE,
                        STRONG_DESCRIPTION,
                        80,
                        baseline,
                        new BudgetContentEvidence(false, true, false),
                        BudgetQualityFlag.UNVERIFIED_PROJECT_METRIC),
                Arguments.of(
                        "unverified project milestone",
                        MegaprojectCategory.BRIDGE,
                        STRONG_DESCRIPTION,
                        80,
                        baseline,
                        new BudgetContentEvidence(false, false, true),
                        BudgetQualityFlag.UNVERIFIED_PROJECT_MILESTONE));
    }

    private static WikidataProjectCost cost(
            String amount,
            String currency,
            Integer year,
            String sourceKey,
            String sourceName,
            String referenceUrl,
            String scopeNote,
            ProjectCostVerificationStatus verificationStatus) {
        BigDecimal value = new BigDecimal(amount);
        return new WikidataProjectCost(
                ProjectCostKind.REPORTED_COST,
                value,
                value,
                currency,
                value,
                value,
                year,
                BigDecimal.ONE,
                LocalDate.of(2020, 1, 1),
                "SPOT",
                "ECB",
                sourceName,
                "https://example.com/source",
                referenceUrl,
                "No normalization required",
                scopeNote,
                sourceKey,
                amount + " " + currency,
                verificationStatus,
                true,
                true);
    }
}
