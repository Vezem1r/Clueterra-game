package io.clueterra.questions.ai.repository;

import io.clueterra.questions.ai.model.embedding.AiEmbeddingCoverage;
import io.clueterra.questions.ai.model.embedding.HintEmbeddingValue;
import io.clueterra.questions.ai.model.embedding.PendingHintEmbedding;
import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class HintEmbeddingRepository {

    private static final String UPSERT_SQL = SqlLoader.load(HintEmbeddingRepository.class, "upsert");
    private static final String FIND_BY_COUNTRY_SQL = SqlLoader.load(HintEmbeddingRepository.class, "findByCountryId");
    private static final String FIND_PENDING_BY_COUNTRY_SQL = SqlLoader.load(HintEmbeddingRepository.class, "findPendingByCountryId");
    private static final String FIND_COUNTRIES_PENDING_SQL = SqlLoader.load(HintEmbeddingRepository.class, "findCountriesPending");
    private static final String COVERAGE_SQL = SqlLoader.load(HintEmbeddingRepository.class, "coverage");

    private final NamedParameterJdbcTemplate jdbc;

    public void upsert(HintEmbeddingValue embedding, String model) {
        jdbc.update(UPSERT_SQL, parameters(embedding, model));
    }

    public void upsertBatch(List<HintEmbeddingValue> embeddings, String model) {
        List<MapSqlParameterSource> params = embeddings.stream()
                .map(embedding -> parameters(embedding, model))
                .toList();
        jdbc.batchUpdate(UPSERT_SQL, params.toArray(MapSqlParameterSource[]::new));
    }

    public Map<Long, float[]> findByCountryId(Long countryId, String model, int dimensions) {
        Map<Long, float[]> result = new LinkedHashMap<>();
        jdbc.query(FIND_BY_COUNTRY_SQL, modelParameters(model, dimensions)
                .addValue("countryId", countryId), (RowCallbackHandler) rs ->
                result.put(rs.getLong("hint_id"), toFloatArray(rs.getArray("vector"))));
        return result;
    }

    public List<PendingHintEmbedding> findHintsPendingEmbedding(
            Long countryId,
            String model,
            int dimensions) {
        return jdbc.query(FIND_PENDING_BY_COUNTRY_SQL, modelParameters(model, dimensions)
                        .addValue("countryId", countryId),
                (rs, rowNum) -> new PendingHintEmbedding(
                        rs.getLong("id"),
                        rs.getString("hint_text"),
                        rs.getString("content_revision_hash")));
    }

    public List<Long> findCountriesPendingEmbedding(int limit, String model, int dimensions) {
        return jdbc.queryForList(FIND_COUNTRIES_PENDING_SQL, modelParameters(model, dimensions)
                .addValue("limit", limit), Long.class);
    }

    public AiEmbeddingCoverage coverage(String model, int dimensions) {
        return jdbc.queryForObject(COVERAGE_SQL, modelParameters(model, dimensions),
                (rs, rowNum) -> new AiEmbeddingCoverage(
                        rs.getLong("active_hints"),
                        rs.getLong("embedded_hints")));
    }

    private MapSqlParameterSource parameters(HintEmbeddingValue embedding, String model) {
        return new MapSqlParameterSource()
                .addValue("hintId", embedding.hintId())
                .addValue("dimensions", embedding.vector().length)
                .addValue("vector", toDoubleArray(embedding.vector()))
                .addValue("model", model)
                .addValue("contentRevisionHash", embedding.contentRevisionHash());
    }

    private MapSqlParameterSource modelParameters(String model, int dimensions) {
        return new MapSqlParameterSource()
                .addValue("model", model)
                .addValue("dimensions", dimensions);
    }

    private Double[] toDoubleArray(float[] vector) {
        Double[] boxed = new Double[vector.length];
        for (int index = 0; index < vector.length; index++) {
            boxed[index] = (double) vector[index];
        }
        return boxed;
    }

    private float[] toFloatArray(Array array) {
        try {
            if (array == null) {
                return new float[0];
            }
            Object raw = array.getArray();
            if (raw instanceof Float[] floats) {
                float[] values = new float[floats.length];
                for (int index = 0; index < floats.length; index++) {
                    values[index] = floats[index] != null ? floats[index] : 0f;
                }
                return values;
            }
            if (raw instanceof Number[] numbers) {
                float[] values = new float[numbers.length];
                for (int index = 0; index < numbers.length; index++) {
                    values[index] = numbers[index] != null ? numbers[index].floatValue() : 0f;
                }
                return values;
            }
            return new float[0];
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read hint embedding vector", exception);
        }
    }
}
