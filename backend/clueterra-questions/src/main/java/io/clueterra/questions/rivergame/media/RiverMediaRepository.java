package io.clueterra.questions.rivergame.media;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RiverMediaRepository {

    private static final String FIND_PHOTO_LIFECYCLE_STATUS =
            SqlLoader.load(RiverMediaRepository.class, "findPhotoLifecycleStatus");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public List<String> findActiveRiverKeys() {
        return jdbc.queryForList("SELECT DISTINCT river_key FROM cl_schema.river_game_question "
                + "WHERE is_active ORDER BY river_key", Map.of(), String.class);
    }

    public Optional<String> findPhotoLifecycleStatus(String originalUrl, String sourcePageUrl) {
        return jdbc.queryForList(FIND_PHOTO_LIFECYCLE_STATUS, new MapSqlParameterSource()
                        .addValue("originalUrl", originalUrl)
                        .addValue("sourcePageUrl", sourcePageUrl), String.class)
                .stream().findFirst();
    }

    @Transactional
    public void save(String riverKey, String wikipediaUrl, Optional<RiverPhoto> photo) {
        jdbc.queryForObject("SELECT cl_schema.save_river_game_media(:riverKey, :wikipediaUrl, CAST(:photo AS JSONB))",
                new MapSqlParameterSource()
                        .addValue("riverKey", riverKey)
                        .addValue("wikipediaUrl", wikipediaUrl)
                        .addValue("photo", photo.map(this::json).orElse(null)),
                Long.class);
    }

    private String json(RiverPhoto photo) {
        try {
            return mapper.writeValueAsString(photo);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode river photo " + photo.objectKey(), error);
        }
    }
}
