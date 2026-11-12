package io.clueterra.questions.ai.repository;

import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.model.generation.AiGenerationImageInventory;
import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AiGenerationGapRepository {

    private static final String FIND_COUNTRIES_NEEDING_GENERATION_SQL = SqlLoader.load(AiGenerationGapRepository.class, "findCountriesNeedingGeneration");
    private static final String FIND_COUNTRIES_FOR_GENERATION_SQL = SqlLoader.load(AiGenerationGapRepository.class, "findCountriesForGeneration");
    private static final String FIND_COUNTRIES_BY_IDS_SQL = SqlLoader.load(AiGenerationGapRepository.class, "findCountriesByIds");
    private static final String FIND_IMAGE_INVENTORY_BY_COUNTRY_ID_SQL = SqlLoader.load(AiGenerationGapRepository.class, "findImageInventoryByCountryId");

    private final NamedParameterJdbcTemplate jdbc;

    public List<AiGenerationCountryGapCandidate> findCountriesNeedingGeneration(int minActiveHintsThreshold, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("minActiveHintsThreshold", minActiveHintsThreshold)
                .addValue("limit", limit);

        return jdbc.query(FIND_COUNTRIES_NEEDING_GENERATION_SQL, params, rowMapper());
    }

    public List<AiGenerationCountryGapCandidate> findCountriesForGeneration(int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit);
        return jdbc.query(FIND_COUNTRIES_FOR_GENERATION_SQL, params, rowMapper());
    }

    public List<AiGenerationCountryGapCandidate> findByCountryIds(List<Long> countryIds, int minActiveHintsThreshold) {
        if (countryIds == null || countryIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query(FIND_COUNTRIES_BY_IDS_SQL,
                new MapSqlParameterSource()
                        .addValue("countryIds", countryIds)
                        .addValue("minActiveHintsThreshold", minActiveHintsThreshold),
                rowMapper()
        );
    }

    public AiGenerationImageInventory findImageInventoryByCountryId(Long countryId) {
        return jdbc.queryForObject(
                FIND_IMAGE_INVENTORY_BY_COUNTRY_ID_SQL,
                new MapSqlParameterSource().addValue("countryId", countryId),
                (rs, rowNum) -> new AiGenerationImageInventory(
                        rs.getInt("landmark_count"),
                        rs.getInt("currency_count"),
                        rs.getTimestamp("last_rejected_landmark_at") != null
                                ? rs.getTimestamp("last_rejected_landmark_at").toInstant()
                                : null,
                        rs.getTimestamp("last_rejected_currency_at") != null
                                ? rs.getTimestamp("last_rejected_currency_at").toInstant()
                                : null));
    }

    private RowMapper<AiGenerationCountryGapCandidate> rowMapper() {
        return (rs, rowNum) -> new AiGenerationCountryGapCandidate(
                rs.getLong("country_id"),
                rs.getString("country_code"),
                rs.getString("country_name"),
                rs.getInt("active_hint_count"),
                rs.getInt("pending_proposal_count"),
                rs.getInt("effective_hint_count"),
                rs.getInt("missing_hint_count"),
                rs.getTimestamp("last_generated_at") != null
                        ? rs.getTimestamp("last_generated_at").toInstant()
                        : null
        );
    }
}
