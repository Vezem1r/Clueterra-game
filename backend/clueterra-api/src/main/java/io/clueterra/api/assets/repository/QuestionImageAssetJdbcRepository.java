package io.clueterra.api.assets.repository;

import java.util.Optional;

import io.clueterra.api.common.utils.loader.SqlLoader;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QuestionImageAssetJdbcRepository {

    private static final String FIND_HINT_IMAGE_REFERENCE =
            SqlLoader.load(QuestionImageAssetJdbcRepository.class, "findHintImageReference");
    private static final String FIND_STATISTIC_IMAGE_REFERENCE =
            SqlLoader.load(QuestionImageAssetJdbcRepository.class, "findStatisticImageReference");

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<QuestionImageReference> findHintImageReference(Long hintId) {
        return find(FIND_HINT_IMAGE_REFERENCE, hintId);
    }

    public Optional<QuestionImageReference> findStatisticImageReference(Long statisticId) {
        return find(FIND_STATISTIC_IMAGE_REFERENCE, statisticId);
    }

    private Optional<QuestionImageReference> find(String sql, Long id) {
        return jdbc.query(
                        sql,
                        new MapSqlParameterSource("id", id),
                        (rs, rowNum) -> new QuestionImageReference(
                                rs.getString("image_url"),
                                rs.getString("original_image_url"),
                                rs.getString("image_source_page_url"),
                                rs.getString("source")))
                .stream()
                .findFirst();
    }
}
