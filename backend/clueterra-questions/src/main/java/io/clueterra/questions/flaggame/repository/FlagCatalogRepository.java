package io.clueterra.questions.flaggame.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.flaggame.model.FlagQuestionDraft;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FlagCatalogRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Transactional
    public int replaceCatalog(List<FlagQuestionDraft> questions) {
        String payload;
        try {
            payload = mapper.writeValueAsString(questions);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode the flag catalog", error);
        }
        Integer changed = jdbc.queryForObject(
                "SELECT cl_schema.replace_flag_game_catalog(CAST(:questions AS JSONB))",
                new MapSqlParameterSource("questions", payload), Integer.class);
        return changed == null ? 0 : changed;
    }
}
