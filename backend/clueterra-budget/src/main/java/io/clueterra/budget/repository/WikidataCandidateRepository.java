package io.clueterra.budget.repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.WikidataCandidate;
import io.clueterra.budget.util.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WikidataCandidateRepository {

    private static final String UPSERT = SqlLoader.load(WikidataCandidateRepository.class, "upsert");
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public void finalizeCategorySnapshot(
            MegaprojectCategory category, List<WikidataCandidate> candidates) {
        jdbc.update(
                """
                        UPDATE cl_budget_staging.wikidata_candidate
                        SET last_fetched = FALSE, updated_at = NOW()
                        WHERE category = :category
                            AND candidate_origin = 'WIKIDATA'
                            AND editorial_status <> 'APPROVED'
                """,
                Map.of("category", category.name()));
        upsertBatch(candidates);
    }

    @Transactional
    public void upsertCandidates(List<WikidataCandidate> candidates) {
        upsertBatch(candidates);
    }

    private void upsertBatch(List<WikidataCandidate> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] parameters =
                candidates.stream().map(this::parameters).toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT, parameters);
    }

    public int republishAvailable() {
        Integer published =
                jdbc.queryForObject(
                        "SELECT cl_budget_schema.republish_available_budget_candidates(NULL, CURRENT_DATE)",
                        Map.of(),
                        Integer.class);
        return published == null ? 0 : published;
    }

    public int retireUnavailableProjects() {
        Integer retired =
                jdbc.queryForObject(
                        "SELECT cl_budget_schema.retire_unavailable_budget_projects()",
                        Map.of(),
                        Integer.class);
        return retired == null ? 0 : retired;
    }

    private MapSqlParameterSource parameters(WikidataCandidate candidate) {
        return new MapSqlParameterSource()
                .addValue("wikidataId", candidate.wikidataId())
                .addValue("name", candidate.name())
                .addValue("description", candidate.description())
                .addValue("countryCode", candidate.countryCode())
                .addValue("countryName", candidate.countryName())
                .addValue("regionCode", candidate.region().name())
                .addValue("category", candidate.category().name())
                .addValue("projectYear", candidate.projectYear())
                .addValue("imageFileTitle", candidate.image().fileTitle())
                .addValue("imagePageId", candidate.image().pageId())
                .addValue("imageSourceSha1", candidate.image().sourceSha1())
                .addValue("imageThumbnailUrl", candidate.image().thumbnailUrl())
                .addValue("imageOriginalUrl", candidate.image().originalUrl())
                .addValue("imageSourcePageUrl", candidate.image().sourcePageUrl())
                .addValue("imageAuthorName", candidate.image().authorName())
                .addValue("imageCredit", candidate.image().credit())
                .addValue("imageLicenseLabel", candidate.image().licenseLabel())
                .addValue("imageLicenseUrl", candidate.image().licenseUrl())
                .addValue("imageDescription", candidate.image().description())
                .addValue("imageMimeType", candidate.image().mimeType())
                .addValue("imageSourceBytes", candidate.image().sourceBytes())
                .addValue("imageWidth", candidate.image().width())
                .addValue("imageHeight", candidate.image().height())
                .addValue("imageQualityScore", candidate.image().qualityScore())
                .addValue("imageObjectKey", candidate.image().objectKey())
                .addValue("sourceCostAmount", candidate.cost().sourceAmount())
                .addValue("sourceCostCurrency", candidate.cost().sourceCurrency())
                .addValue("answerCostUsd", candidate.cost().amountUsd())
                .addValue("costYear", candidate.cost().costYear())
                .addValue("costUnitsPerUsd", candidate.cost().unitsPerUsd())
                .addValue("costExchangeRateDate", candidate.cost().exchangeRateDate())
                .addValue("costSourceName", candidate.cost().sourceName())
                .addValue("costSourceUrl", candidate.cost().sourceUrl())
                .addValue("costReferenceUrl", nullIfBlank(candidate.cost().referenceUrl()))
                .addValue("costNormalizationNote", candidate.cost().normalizationNote())
                .addValue("sourceUrl", candidate.sourceUrl())
                .addValue("sourcePayload", candidate.sourcePayload())
                .addValue("sitelinkCount", candidate.sitelinkCount())
                .addValue("monthlyPageviews", candidate.monthlyPageviews())
                .addValue("interestScore", candidate.interestScore())
                .addValue("qualityScore", candidate.qualityScore())
                .addValue("qualityStatus", candidate.qualityAssessment().status().name())
                .addValue("evidenceTier", candidate.qualityAssessment().evidenceTier().name())
                .addValue("costConfidenceScore", candidate.qualityAssessment().confidenceScore())
                .addValue("qualityFlags", qualityFlags(candidate))
                .addValue("difficulty", candidate.difficulty().name())
                .addValue("sourceRank", candidate.sourceRank());
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String qualityFlags(WikidataCandidate candidate) {
        return candidate.qualityAssessment().flags().stream()
                .map(flag -> "\"" + flag.name() + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
