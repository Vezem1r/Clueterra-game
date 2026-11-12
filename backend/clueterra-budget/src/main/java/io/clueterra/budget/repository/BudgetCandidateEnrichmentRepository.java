package io.clueterra.budget.repository;

import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.budget.project.BudgetCandidateImageFailure;
import io.clueterra.budget.project.BudgetCandidateImageWorkItem;
import io.clueterra.budget.project.BudgetCandidateStoredImage;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.util.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BudgetCandidateEnrichmentRepository {

    private static final String COUNT_PENDING_IMAGE_CANDIDATES = SqlLoader.load(BudgetCandidateEnrichmentRepository.class, "countPendingImageCandidates");
    private static final String FIND_FAILED_IMAGE_CANDIDATES = SqlLoader.load(BudgetCandidateEnrichmentRepository.class, "findFailedImageCandidates");

    private final NamedParameterJdbcTemplate jdbc;

    public int deduplicateCandidates() {
        Integer updated = jdbc.queryForObject("SELECT cl_budget_schema.deduplicate_budget_candidates()", Map.of(), Integer.class);
        return updated == null ? 0 : updated;
    }

    public List<BudgetCandidateImageWorkItem> claimImageBatch(int limit, Instant staleBefore) {
        return jdbc.query(
                "SELECT * FROM cl_budget_schema.claim_budget_candidate_images(:limit, :staleBefore)",
                claimParameters(limit, staleBefore),
                (resultSet, rowNumber) ->
                        new BudgetCandidateImageWorkItem(
                                resultSet.getString("wikidata_id"),
                                MegaprojectCategory.valueOf(resultSet.getString("category")),
                                resultSet.getString("image_file_title"),
                                resultSet.getString("image_author_name_override"),
                                resultSet.getString("image_credit_override"),
                                resultSet.getInt("attempt_count")));
    }

    public int countPendingImageCandidates(Instant staleBefore) {
        Integer count = jdbc.queryForObject(COUNT_PENDING_IMAGE_CANDIDATES, claimParameters(1, staleBefore), Integer.class);
        return count == null ? 0 : count;
    }

    public List<BudgetCandidateImageFailure> findFailedImageCandidates(int limit) {
        return jdbc.query(
                FIND_FAILED_IMAGE_CANDIDATES,
                Map.of("limit", limit),
                (resultSet, rowNumber) ->
                        new BudgetCandidateImageFailure(
                                resultSet.getString("wikidata_id"),
                                resultSet.getString("category"),
                                resultSet.getString("name"),
                                resultSet.getString("image_failure_reason")));
    }

    public void publishImage(
            BudgetCandidateImageWorkItem candidate,
            BudgetCandidateStoredImage image,
            LocalDate snapshotDate) {
        jdbc.queryForObject(
                """
                        SELECT cl_budget_schema.publish_budget_candidate_image(
                            :wikidataId, :category, :pageId, :sourceSha1, :fileTitle,
                            :objectKey, :originalUrl, :sourcePageUrl, :authorName,
                            :credit, :licenseLabel, :licenseUrl, :description, :mimeType,
                            :sourceBytes, :width, :height, :qualityScore, :snapshotDate)
                        """,
                imageParameters(candidate, image, snapshotDate),
                Long.class);
    }

    public void failImage(BudgetCandidateImageWorkItem candidate, String failureReason) {
        jdbc.queryForObject(
                "SELECT cl_budget_schema.fail_budget_candidate_image("
                        + ":wikidataId, :category, :failureReason)",
                Map.of(
                        "wikidataId",
                        candidate.wikidataId(),
                        "category",
                        candidate.category().name(),
                        "failureReason",
                        failureReason),
                Boolean.class);
    }

    public int resetImageFailures() {
        Integer updated = jdbc.queryForObject("SELECT cl_budget_schema.reset_budget_candidate_image_failures()", Map.of(), Integer.class);
        return updated == null ? 0 : updated;
    }

    private MapSqlParameterSource claimParameters(int limit, Instant staleBefore) {
        return new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue(
                        "staleBefore",
                        OffsetDateTime.ofInstant(staleBefore, ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private MapSqlParameterSource imageParameters(
            BudgetCandidateImageWorkItem candidate,
            BudgetCandidateStoredImage image,
            LocalDate snapshotDate) {
        return new MapSqlParameterSource()
                .addValue("wikidataId", candidate.wikidataId())
                .addValue("category", candidate.category().name())
                .addValue("pageId", image.pageId())
                .addValue("sourceSha1", image.sourceSha1())
                .addValue("fileTitle", image.fileTitle())
                .addValue("objectKey", image.objectKey())
                .addValue("originalUrl", image.originalUrl())
                .addValue("sourcePageUrl", image.sourcePageUrl())
                .addValue("authorName", image.authorName())
                .addValue("credit", image.credit())
                .addValue("licenseLabel", image.licenseLabel())
                .addValue("licenseUrl", image.licenseUrl())
                .addValue("description", image.description())
                .addValue("mimeType", image.mimeType())
                .addValue("sourceBytes", image.sourceBytes())
                .addValue("width", image.width())
                .addValue("height", image.height())
                .addValue("qualityScore", image.qualityScore())
                .addValue("snapshotDate", snapshotDate);
    }
}
