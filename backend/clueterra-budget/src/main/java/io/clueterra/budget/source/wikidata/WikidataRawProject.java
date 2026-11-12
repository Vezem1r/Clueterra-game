package io.clueterra.budget.source.wikidata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.clueterra.budget.project.ProjectMilestoneType;

public record WikidataRawProject(
    String wikidataId,
    String countryWikidataId,
    String countryCode,
    List<String> imageFileTitles,
    Integer projectYear,
    int sitelinkCount,
    WikidataRawCost cost,
    List<WikidataRawCost> costClaims,
    List<WikidataRawMetric> metrics,
    List<WikidataRawMilestone> milestones,
    List<JsonNode> sourceFacts) {

  public WikidataRawProject {
    imageFileTitles = List.copyOf(imageFileTitles);
    costClaims = List.copyOf(costClaims);
    metrics = List.copyOf(metrics);
    milestones = List.copyOf(milestones);
    sourceFacts = List.copyOf(sourceFacts);
  }

  public WikidataRawProject withSupplementalData(
      List<WikidataRawCost> additionalCosts,
      List<WikidataRawMetric> additionalMetrics,
      List<WikidataRawMilestone> additionalMilestones) {
    List<WikidataRawCost> mergedCosts = new ArrayList<>(costClaims);
    mergedCosts.addAll(additionalCosts);
    WikidataRawCost selected =
        mergedCosts.stream()
            .filter(value -> value.costKind().supportsHowMuch())
            .reduce(WikidataRawCost::prefer)
            .orElse(cost);
    List<WikidataRawMetric> mergedMetrics = new ArrayList<>(metrics);
    mergedMetrics.addAll(additionalMetrics);
    List<WikidataRawMilestone> mergedMilestones = new ArrayList<>(milestones);
    mergedMilestones.addAll(additionalMilestones);
    Integer resolvedProjectYear =
        Optional.ofNullable(projectYear).orElseGet(() -> projectYear(mergedMilestones));
    return new WikidataRawProject(
        wikidataId,
        countryWikidataId,
        countryCode,
        imageFileTitles,
        resolvedProjectYear,
        sitelinkCount,
        selected,
        mergedCosts,
        mergedMetrics,
        mergedMilestones,
        sourceFacts);
  }

  private Integer projectYear(List<WikidataRawMilestone> values) {
    return values.stream()
        .filter(
            milestone ->
                milestone.milestoneType() == ProjectMilestoneType.ACTUAL_COMPLETION
                    || milestone.milestoneType() == ProjectMilestoneType.OPENING)
        .filter(milestone -> milestone.milestoneDate() != null || milestone.milestoneYear() != null)
        .min(Comparator.comparingInt(this::milestonePriority))
        .map(
            milestone ->
                milestone.milestoneDate() == null
                    ? milestone.milestoneYear()
                    : milestone.milestoneDate().getYear())
        .orElse(null);
  }

  private int milestonePriority(WikidataRawMilestone milestone) {
    return milestone.milestoneType() == ProjectMilestoneType.ACTUAL_COMPLETION ? 0 : 1;
  }
}
