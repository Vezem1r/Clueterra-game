package io.clueterra.questions.ai.repository;

import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.model.review.AiReviewCoverage;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AiReviewQueueRepository {

    private static final String CLAIM_PENDING_REVIEW_CANDIDATES_SQL = SqlLoader.load(AiReviewQueueRepository.class, "claimPendingReviewCandidates");
    private static final String FIND_REVIEW_CANDIDATES_BY_IDS_SQL = SqlLoader.load(AiReviewQueueRepository.class, "findReviewCandidatesByIds");
    private static final String RELEASE_RUN_CLAIMS_SQL = SqlLoader.load(AiReviewQueueRepository.class, "releaseRunClaims");
    private static final String RELEASE_INACTIVE_RUN_CLAIMS_SQL = SqlLoader.load(AiReviewQueueRepository.class, "releaseInactiveRunClaims");
    private static final String FIND_REVIEW_COVERAGE_SQL = SqlLoader.load(AiReviewQueueRepository.class, "findReviewCoverage");

    private final NamedParameterJdbcTemplate jdbc;

    public List<AiReviewCandidate> claimPendingCandidates(Long runId, int limit) {
        return jdbc.query(CLAIM_PENDING_REVIEW_CANDIDATES_SQL,
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("limit", limit),
                rowMapper()
        );
    }

    public int releaseRunClaims(Long runId) {
        return jdbc.update(RELEASE_RUN_CLAIMS_SQL, new MapSqlParameterSource("runId", runId));
    }

    public int releaseInactiveRunClaims() {
        return jdbc.update(RELEASE_INACTIVE_RUN_CLAIMS_SQL, new MapSqlParameterSource());
    }

    public List<AiReviewCandidate> findByHintIds(List<Long> hintIds) {
        if (hintIds == null || hintIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query(FIND_REVIEW_CANDIDATES_BY_IDS_SQL,
                new MapSqlParameterSource("hintIds", hintIds),
                rowMapper()
        );
    }

    public AiReviewCoverage findReviewCoverage() {
        return jdbc.queryForObject(
                FIND_REVIEW_COVERAGE_SQL,
                new MapSqlParameterSource(),
                (rs, rowNum) ->
                        new AiReviewCoverage(
                                rs.getLong("processed_hints"),
                                rs.getLong("total_hints"),
                                rs.getLong("pending_hints"),
                                rs.getLong("in_review_hints"),
                                rs.getLong("action_pending_hints")
                        ));
    }

    private RowMapper<AiReviewCandidate> rowMapper() {
        return (rs, rowNum) -> new AiReviewCandidate(
                rs.getLong("hint_id"),
                rs.getLong("country_id"),
                rs.getString("country_code"),
                rs.getString("country_name"),
                rs.getString("country_region"),
                rs.getString("country_subregion"),
                rs.getString("country_capital"),
                rs.getBigDecimal("difficulty_modifier"),
                Category.valueOf(rs.getString("category")),
                HintType.valueOf(rs.getString("hint_type")),
                rs.getString("hint_text"),
                rs.getString("image_url"),
                rs.getString("original_image_url"),
                getInt(rs, "difficulty"),
                rs.getBigDecimal("quality_score"),
                DataSource.valueOf(rs.getString("source")),
                rs.getString("source_indicator"),
                getInt(rs, "source_year"),
                rs.getString("content_revision_hash"),
                HintReviewStatus.valueOf(rs.getString("review_status")),
                getInstant(rs, "updated_at"),
                getInstant(rs, "last_reviewed_at")
        );
    }

    private Integer getInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }
}
