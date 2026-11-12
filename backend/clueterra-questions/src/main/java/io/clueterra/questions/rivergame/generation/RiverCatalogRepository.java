package io.clueterra.questions.rivergame.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class RiverCatalogRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public Set<String> findMissingCountryCodes(Set<String> countryCodes) {
        if (countryCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> missing = new TreeSet<>(countryCodes);
        jdbc.queryForList("SELECT iso_alpha2 FROM cl_schema.countries WHERE iso_alpha2 IN (:codes)",
                new MapSqlParameterSource("codes", countryCodes), String.class).forEach(missing::remove);
        return missing;
    }

    @Transactional
    public int replaceCountry(String countryCode, List<RiverCatalogPreparedEntry> entries) {
        String payload;
        try {
            payload = mapper.writeValueAsString(entries);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode river catalog for " + countryCode, error);
        }
        Integer changed = jdbc.queryForObject(
                "SELECT cl_schema.replace_river_game_country(:code, CAST(:questions AS JSONB))",
                new MapSqlParameterSource().addValue("code", countryCode).addValue("questions", payload),
                Integer.class);
        return changed == null ? 0 : changed;
    }

    @Transactional
    public int retireAbsentCountries(Set<String> presentCodes) {
        if (presentCodes.isEmpty()) {
            throw new IllegalArgumentException("Cannot retire countries using an empty river catalog");
        }
        return jdbc.update("UPDATE cl_schema.river_game_question question SET is_active = FALSE, updated_at = NOW() "
                        + "FROM cl_schema.countries country WHERE country.id = question.country_id "
                        + "AND question.is_active AND country.iso_alpha2 NOT IN (:codes)",
                Map.of("codes", presentCodes));
    }
}
