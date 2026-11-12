package io.clueterra.questions.quizgame.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.quizgame.model.QuizCountryDraft;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QuizCatalogRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Transactional
    public int replaceCatalog(List<QuizCountryDraft> countries) {
        String payload;
        try {
            payload = mapper.writeValueAsString(countries);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode the quiz catalog", error);
        }
        Integer changed = jdbc.queryForObject(
                "SELECT cl_schema.replace_quiz_game_catalog(CAST(:countries AS JSONB))",
                new MapSqlParameterSource("countries", payload), Integer.class);
        return changed == null ? 0 : changed;
    }
}
