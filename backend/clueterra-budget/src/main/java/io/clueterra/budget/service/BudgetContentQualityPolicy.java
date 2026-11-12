package io.clueterra.budget.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.clueterra.budget.project.BudgetContentEvidence;
import io.clueterra.budget.project.BudgetQualityAssessment;
import io.clueterra.budget.project.BudgetQualityFlag;
import io.clueterra.budget.project.ContentQualityStatus;
import io.clueterra.budget.project.CostEvidenceTier;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostVerificationStatus;
import io.clueterra.budget.project.WikidataProjectCost;

@Component
public class BudgetContentQualityPolicy {

  private static final Pattern GENERIC_DESCRIPTION =
      Pattern.compile(
          "^(airport|bridge|dam|energy project|metro|railway|skyscraper|stadium|tunnel)\\.?$");
  private static final Set<BudgetQualityFlag> AUTO_APPROVAL_BLOCKERS =
      Set.of(
          BudgetQualityFlag.WEAK_DESCRIPTION,
          BudgetQualityFlag.CATEGORY_COST_OUTLIER,
          BudgetQualityFlag.CURRENCY_ERA_MISMATCH,
          BudgetQualityFlag.PROJECT_PART_COST,
          BudgetQualityFlag.LEAD_ONLY_COST,
          BudgetQualityFlag.LOW_AUTHORITY_COST,
          BudgetQualityFlag.COST_SOURCE_CONFLICT,
          BudgetQualityFlag.NON_AUTHORITATIVE_COST_SOURCE,
          BudgetQualityFlag.UNVERIFIED_PROJECT_METADATA,
          BudgetQualityFlag.UNVERIFIED_PROJECT_METRIC,
          BudgetQualityFlag.UNVERIFIED_PROJECT_MILESTONE);
  private static final Map<MegaprojectCategory, BigDecimal> CATEGORY_COST_CEILINGS =
      Map.ofEntries(
          Map.entry(MegaprojectCategory.BRIDGE, new BigDecimal("100000000000")),
          Map.entry(MegaprojectCategory.METRO_RAIL, new BigDecimal("250000000000")),
          Map.entry(MegaprojectCategory.STADIUM, new BigDecimal("15000000000")),
          Map.entry(MegaprojectCategory.SKYSCRAPER, new BigDecimal("20000000000")),
          Map.entry(MegaprojectCategory.AIRPORT, new BigDecimal("150000000000")),
          Map.entry(MegaprojectCategory.TUNNEL, new BigDecimal("100000000000")),
          Map.entry(MegaprojectCategory.DAM, new BigDecimal("150000000000")),
          Map.entry(MegaprojectCategory.ENERGY, new BigDecimal("250000000000")),
          Map.entry(MegaprojectCategory.SPACE, new BigDecimal("250000000000")),
          Map.entry(MegaprojectCategory.ROAD_HIGHWAY, new BigDecimal("500000000000")),
          Map.entry(MegaprojectCategory.PORT, new BigDecimal("500000000000")));

  public BudgetQualityAssessment assess(
      MegaprojectCategory category,
      String description,
      int catalogQualityScore,
      WikidataProjectCost cost,
      BudgetContentEvidence evidence) {
    CostEvidenceTier tier = evidenceTier(cost);
    List<BudgetQualityFlag> flags = new ArrayList<>();
    addIf(flags, isWeakDescription(description), BudgetQualityFlag.WEAK_DESCRIPTION);
    boolean costOutlier = cost.amountUsd().compareTo(CATEGORY_COST_CEILINGS.get(category)) > 0;
    addIf(flags, costOutlier, BudgetQualityFlag.CATEGORY_COST_OUTLIER);
    addIf(flags, currencyEraMismatch(cost), BudgetQualityFlag.CURRENCY_ERA_MISMATCH);
    boolean projectPart =
        cost.scopeNote() != null && cost.scopeNote().startsWith("Applies to project part");
    addIf(flags, projectPart, BudgetQualityFlag.PROJECT_PART_COST);
    addIf(flags, cost.costYear() == null, BudgetQualityFlag.COST_YEAR_MISSING);
    addIf(flags, tier == CostEvidenceTier.ENCYCLOPEDIA_LEAD, BudgetQualityFlag.LEAD_ONLY_COST);
    boolean lowAuthority =
        tier == CostEvidenceTier.ENCYCLOPEDIA_SNAPSHOT && catalogQualityScore < 60;
    addIf(flags, lowAuthority, BudgetQualityFlag.LOW_AUTHORITY_COST);
    boolean disputed = cost.verificationStatus() == ProjectCostVerificationStatus.DISPUTED;
    addIf(flags, disputed, BudgetQualityFlag.COST_SOURCE_CONFLICT);
    addIf(
        flags,
        tier != CostEvidenceTier.OFFICIAL_SOURCE,
        BudgetQualityFlag.NON_AUTHORITATIVE_COST_SOURCE);
    addIf(
        flags,
        evidence.unverifiedProjectMetadata(),
        BudgetQualityFlag.UNVERIFIED_PROJECT_METADATA);
    addIf(flags, evidence.unverifiedMetrics(), BudgetQualityFlag.UNVERIFIED_PROJECT_METRIC);
    addIf(
        flags,
        evidence.unverifiedMilestones(),
        BudgetQualityFlag.UNVERIFIED_PROJECT_MILESTONE);

    int corroborationBonus =
        cost.verificationStatus() == ProjectCostVerificationStatus.CORROBORATED ? 8 : 0;
    int rawConfidence =
        baseConfidence(tier)
            + qualityAdjustment(catalogQualityScore)
            + corroborationBonus
            - flags.size() * 8;
    int confidence = Math.clamp(rawConfidence, 0, 100);
    boolean blocked = flags.stream().anyMatch(AUTO_APPROVAL_BLOCKERS::contains);
    ContentQualityStatus status =
        blocked || confidence < 65
            ? ContentQualityStatus.NEEDS_REVIEW
            : ContentQualityStatus.AUTO_APPROVED;
    return new BudgetQualityAssessment(status, tier, confidence, flags);
  }

  public boolean isWeakDescription(String description) {
    if (description == null) {
      return true;
    }
    String normalized = description.strip().toLowerCase(Locale.ROOT);
    return normalized.length() < 40 || GENERIC_DESCRIPTION.matcher(normalized).matches();
  }

  private CostEvidenceTier evidenceTier(WikidataProjectCost cost) {
    if (cost.sourceKey().startsWith("NASA_")) {
      return CostEvidenceTier.OFFICIAL_SOURCE;
    }
    if ("P2130".equals(cost.sourceKey()) || "P2769".equals(cost.sourceKey())) {
      return cost.referenceUrl() == null || cost.referenceUrl().isBlank()
          ? CostEvidenceTier.UNREFERENCED_KNOWLEDGE_BASE
          : CostEvidenceTier.REFERENCED_KNOWLEDGE_BASE;
    }
    if (cost.sourceKey().startsWith("WIKIPEDIA_LEAD")) {
      return CostEvidenceTier.ENCYCLOPEDIA_LEAD;
    }
    if (cost.sourceName().contains("(cited)")) {
      return CostEvidenceTier.CITED_ENCYCLOPEDIA;
    }
    return CostEvidenceTier.ENCYCLOPEDIA_SNAPSHOT;
  }

  private boolean currencyEraMismatch(WikidataProjectCost cost) {
    if (cost.costYear() == null) {
      return false;
    }
    return "EUR".equals(cost.sourceCurrency()) && cost.costYear() < 1999
        || "DEM".equals(cost.sourceCurrency()) && cost.costYear() > 2001;
  }

  private int baseConfidence(CostEvidenceTier tier) {
    return switch (tier) {
      case OFFICIAL_SOURCE -> 94;
      case PRIMARY_DOCUMENT -> 92;
      case REFERENCED_KNOWLEDGE_BASE -> 86;
      case CITED_ENCYCLOPEDIA -> 82;
      case UNREFERENCED_KNOWLEDGE_BASE -> 72;
      case ENCYCLOPEDIA_SNAPSHOT -> 68;
      case ENCYCLOPEDIA_LEAD -> 52;
    };
  }

  private int qualityAdjustment(int catalogQualityScore) {
    return Math.clamp((catalogQualityScore - 60) / 5, -10, 6);
  }

  private void addIf(List<BudgetQualityFlag> flags, boolean condition, BudgetQualityFlag flag) {
    if (condition) {
      flags.add(flag);
    }
  }
}
