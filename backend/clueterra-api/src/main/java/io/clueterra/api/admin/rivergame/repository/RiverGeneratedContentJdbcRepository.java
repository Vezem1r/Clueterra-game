package io.clueterra.api.admin.rivergame.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.api.admin.rivergame.dto.RiverHintPreviewDto;
import io.clueterra.api.admin.rivergame.dto.RiverQuestionPreviewDto;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.loader.SqlLoader;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RiverGeneratedContentJdbcRepository {

    private static final String FIND_QUESTIONS =
            SqlLoader.load(RiverGeneratedContentJdbcRepository.class, "findQuestions");
    private static final String COUNT_QUESTIONS =
            SqlLoader.load(RiverGeneratedContentJdbcRepository.class, "countQuestions");
    private static final Map<String, String> SORT_ORDER = Map.of(
            "COUNTRY", "c.name_en, q.rank_in_country, q.id",
            "NAME", "q.name_en, c.name_en, q.id",
            "LONGEST", "q.length_km DESC, q.id",
            "SHORTEST", "q.length_km, q.id",
            "EASIEST", "q.rank_in_country, c.name_en, q.id",
            "HARDEST", "q.rank_in_country DESC, c.name_en, q.id",
            "NEWEST", "q.updated_at DESC, q.id DESC");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public List<RiverQuestionPreviewDto> findQuestions(
            String countryCode, String search, Boolean active, String sort, int limit, int offset) {
        QueryFilterBuilder filter = filters(countryCode, search, active);
        filter.params().addValue("limit", limit).addValue("offset", offset);
        String order = SORT_ORDER.getOrDefault(sort, SORT_ORDER.get("COUNTRY"));
        return jdbc.query(FIND_QUESTIONS + filter.whereClause()
                        + " GROUP BY q.id, c.iso_alpha2, c.name_en, media.wikipedia_url, photo.object_key,"
                        + " photo.author_name, photo.license_label ORDER BY " + order
                        + " LIMIT :limit OFFSET :offset",
                filter.params(),
                (rs, row) -> new RiverQuestionPreviewDto(
                        rs.getLong("id"), rs.getString("country_code"), rs.getString("country_name"),
                        rs.getString("name_en"), rs.getInt("rank_in_country"),
                        rs.getDouble("length_km"), url(rs.getString("plain_image_key")),
                        url(rs.getString("borders_image_key")), rs.getBoolean("is_active"),
                        hints(rs.getString("hints")), photoUrl(rs.getString("photo_object_key")),
                        rs.getString("photo_author_name"), rs.getString("photo_license_label"),
                        rs.getString("wikipedia_url")));
    }

    public long countQuestions(String countryCode, String search, Boolean active) {
        QueryFilterBuilder filter = filters(countryCode, search, active);
        Long count = jdbc.queryForObject(COUNT_QUESTIONS + filter.whereClause(), filter.params(), Long.class);
        return count == null ? 0 : count;
    }

    private QueryFilterBuilder filters(String countryCode, String search, Boolean active) {
        return new QueryFilterBuilder()
                .eq("c.iso_alpha2", "countryCode", countryCode)
                .ilike("LOWER(q.name_en) LIKE :search OR LOWER(c.name_en) LIKE :search",
                        "search", search)
                .bool("q.is_active", "active", active);
    }

    private String url(String key) {
        return "/assets/rivers/" + key.substring("river-game/".length());
    }

    private String photoUrl(String key) {
        return key == null ? null : "/assets/rivers/photos/" + key.substring("river-photos/".length());
    }

    private List<RiverHintPreviewDto> hints(String json) {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, RiverHintPreviewDto.class);
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot decode river question hints", error);
        }
    }
}
