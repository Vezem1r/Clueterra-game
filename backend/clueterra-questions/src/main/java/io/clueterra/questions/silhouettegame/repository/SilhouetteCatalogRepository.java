package io.clueterra.questions.silhouettegame.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.repository.util.SqlLoader;
import io.clueterra.questions.silhouettegame.model.SilhouetteQuestionDraft;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SilhouetteCatalogRepository {

    private static final String FIND_COUNTRIES_WITH_PHOTO = SqlLoader.load(SilhouetteCatalogRepository.class, "findCountriesWithPhoto");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public boolean hasQuestions() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM cl_schema.silhouette_game_question)", Map.of(), Boolean.class));
    }

    public Set<String> findCountriesWithPhoto() {
        return Set.copyOf(jdbc.queryForList(FIND_COUNTRIES_WITH_PHOTO, Map.of(), String.class));
    }

    @Transactional
    public int replaceCatalog(List<SilhouetteQuestionDraft> questions) {
        String payload;
        try {
            payload = mapper.writeValueAsString(questions);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode the silhouette catalog", error);
        }
        Integer changed = jdbc.queryForObject(
                "SELECT cl_schema.replace_silhouette_game_catalog(CAST(:questions AS JSONB))",
                new MapSqlParameterSource("questions", payload), Integer.class);
        return changed == null ? 0 : changed;
    }
}
