package io.clueterra.budget.project;

public record WikidataCandidate(
    String wikidataId,
    String name,
    String description,
    String countryCode,
    String countryName,
    CandidateRegion region,
    MegaprojectCategory category,
    Integer projectYear,
    BudgetDifficulty difficulty,
    WikidataCandidateImage image,
    WikidataProjectCost cost,
    String sourceUrl,
    String sourcePayload,
    int sitelinkCount,
    int monthlyPageviews,
    int interestScore,
    int qualityScore,
    BudgetQualityAssessment qualityAssessment,
    int sourceRank) {

  public WikidataCandidate withCatalogMetadata(
      BudgetDifficulty catalogDifficulty, int catalogQualityScore, int catalogSourceRank) {
    return new WikidataCandidate(
        wikidataId,
        name,
        description,
        countryCode,
        countryName,
        region,
        category,
        projectYear,
        catalogDifficulty,
        image,
        cost,
        sourceUrl,
        sourcePayload,
        sitelinkCount,
        monthlyPageviews,
        interestScore,
        catalogQualityScore,
        qualityAssessment,
        catalogSourceRank);
  }
}
