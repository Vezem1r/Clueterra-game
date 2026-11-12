package io.clueterra.questions.countryphotos.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.countryphotos.model.CountryPhoto;
import io.clueterra.questions.countryphotos.model.CountryPhotoCandidate;
import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CountryPhotoRepository {

    private static final String FIND_CANDIDATES = SqlLoader.load(CountryPhotoRepository.class, "findCandidates");
    private static final String FIND_PHOTO_LIFECYCLE_STATUS = SqlLoader.load(CountryPhotoRepository.class, "findPhotoLifecycleStatus");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public List<CountryPhotoCandidate> findCandidates() {
        return jdbc.query(FIND_CANDIDATES, Map.of(), (rs, row) -> new CountryPhotoCandidate(
                rs.getLong("country_id"), rs.getString("iso2"), rs.getString("source_page_url")));
    }

    public Set<String> findManualCountries() {
        return Set.copyOf(jdbc.queryForList(
                "SELECT iso2 FROM cl_schema.silhouette_game_question WHERE photo_selection = 'MANUAL'", Map.of(), String.class));
    }

    public Optional<String> findPhotoLifecycleStatus(String originalUrl, String sourcePageUrl) {
        return jdbc.queryForList(FIND_PHOTO_LIFECYCLE_STATUS, new MapSqlParameterSource()
                        .addValue("originalUrl", originalUrl)
                        .addValue("sourcePageUrl", sourcePageUrl), String.class)
                .stream().findFirst();
    }

    @Transactional
    public boolean save(String iso2, Optional<CountryPhoto> photo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT cl_schema.save_silhouette_game_photo(:iso2, CAST(:photo AS JSONB))",
                new MapSqlParameterSource()
                        .addValue("iso2", iso2)
                        .addValue("photo", photo.map(this::json).orElse(null)),
                Boolean.class));
    }

    private String json(CountryPhoto photo) {
        try {
            return mapper.writeValueAsString(photo);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode country photo " + photo.objectKey(), error);
        }
    }
}
