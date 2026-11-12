package io.clueterra.api.admin.silhouettegame.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.silhouettegame.dto.SilhouetteHintPreviewDto;
import io.clueterra.api.admin.silhouettegame.dto.SilhouetteQuestionPreviewDto;
import io.clueterra.api.admin.silhouettegame.dto.SilhouetteQuestionSummaryDto;
import io.clueterra.api.admin.silhouettegame.model.SilhouettePhotoUpdate;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.loader.SqlLoader;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SilhouetteGeneratedContentJdbcRepository {

    private static final String FIND_QUESTIONS =
            SqlLoader.load(SilhouetteGeneratedContentJdbcRepository.class, "findQuestions");
    private static final String FIND_SUMMARY =
            SqlLoader.load(SilhouetteGeneratedContentJdbcRepository.class, "findSummary");
    private static final String FIND_PHOTO_LIFECYCLE_STATUS =
            SqlLoader.load(SilhouetteGeneratedContentJdbcRepository.class, "findPhotoLifecycleStatus");
    private static final String SAVE_PHOTO = "SELECT cl_schema.save_silhouette_game_photo("
            + ":iso2, CAST(:photo AS JSONB), CAST(:selectedBy AS TEXT))";
    private static final String RELEASE_PHOTO = "UPDATE cl_schema.silhouette_game_question "
            + "SET photo_selection = 'AUTO', photo_selected_by = NULL WHERE iso2 = :iso2 AND photo_selection = 'MANUAL'";
    private static final String PHOTO_KEY_PREFIX = "country-photos/";
    private static final Map<String, String> PHOTO_PREDICATES = Map.of(
            "WITH_PHOTO", "photo.object_key IS NOT NULL",
            "MISSING", "photo.object_key IS NULL",
            "MANUAL", "q.photo_selection = 'MANUAL'");
    private static final String PHOTO_URL_PREFIX = "/api/assets/countries/photos/";
    private static final Map<String, String> SORT_ORDER = Map.of(
            "COUNTRY", "q.names ->> 'en', q.id",
            "EASIEST", "q.tier, q.names ->> 'en', q.id",
            "HARDEST", "q.tier DESC, q.names ->> 'en', q.id",
            "MOST_PLAYED", "stats.players DESC, q.names ->> 'en', q.id",
            "MOST_SKIPPED", "stats.skips DESC, q.names ->> 'en', q.id",
            "NEWEST", "q.generated_at DESC, q.id DESC");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public List<SilhouetteQuestionPreviewDto> findQuestions(String countryCode, String search, Boolean active, Integer tier,
            String photo, String sort, int limit, int offset) {
        QueryFilterBuilder filter = filters(countryCode, search, active, tier);
        Optional.ofNullable(PHOTO_PREDICATES.get(photo)).ifPresent(filter::predicate);
        filter.params().addValue("limit", limit).addValue("offset", offset);
        String order = SORT_ORDER.getOrDefault(sort, SORT_ORDER.get("COUNTRY"));
        return jdbc.query(FIND_QUESTIONS + filter.whereClause() + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                filter.params(),
                (rs, row) -> new SilhouetteQuestionPreviewDto(
                        rs.getLong("id"), rs.getString("iso2"), rs.getString("name_en"), rs.getInt("tier"),
                        rs.getString("region"), rs.getString("subregion"), rs.getString("shape_path"),
                        rs.getBoolean("is_active"), rs.getString("facts"), hints(rs.getString("hints")),
                        Optional.ofNullable(rs.getString("photo_object_key"))
                                .map(key -> PHOTO_URL_PREFIX + key.substring(PHOTO_KEY_PREFIX.length())).orElse(null),
                        rs.getString("photo_author_name"), rs.getString("photo_source_page_url"),
                        rs.getString("photo_license_label"), rs.getString("photo_license_url"),
                        (Integer) rs.getObject("photo_width"), (Integer) rs.getObject("photo_height"),
                        rs.getString("photo_selection"), rs.getString("photo_selected_by"), rs.getString("landmark_qid"),
                        rs.getString("landmark_name"), (Integer) rs.getObject("landmark_sitelinks"),
                        rs.getLong("players"), rs.getLong("solved"), rs.getLong("skips"), rs.getLong("reveals"),
                        rs.getObject("average_hints") == null ? null : rs.getDouble("average_hints"),
                        timestamp(rs.getTimestamp("generated_at"))));
    }

    public Optional<SilhouetteQuestionPreviewDto> findQuestion(String iso2) {
        return findQuestions(iso2, "", null, null, "ALL", "COUNTRY", 1, 0).stream().findFirst();
    }

    public Optional<String> findPhotoLifecycleStatus(String originalUrl, String sourcePageUrl) {
        return jdbc.queryForList(FIND_PHOTO_LIFECYCLE_STATUS, new MapSqlParameterSource()
                        .addValue("originalUrl", originalUrl)
                        .addValue("sourcePageUrl", sourcePageUrl), String.class)
                .stream().findFirst();
    }

    public void saveManual(String iso2, Optional<SilhouettePhotoUpdate> photo, String selectedBy) {
        jdbc.queryForObject(SAVE_PHOTO, new MapSqlParameterSource()
                .addValue("iso2", iso2)
                .addValue("photo", photo.map(this::json).orElse(null))
                .addValue("selectedBy", selectedBy), Boolean.class);
    }

    public boolean release(String iso2) {
        return jdbc.update(RELEASE_PHOTO, Map.of("iso2", iso2)) > 0;
    }

    public SilhouetteQuestionSummaryDto findSummary() {
        return jdbc.queryForObject(FIND_SUMMARY, Map.of(), (rs, row) -> new SilhouetteQuestionSummaryDto(
                rs.getLong("active_questions"), rs.getLong("landmark_clues"), rs.getLong("players"),
                rs.getLong("solves"), rs.getLong("guesses")));
    }

    private QueryFilterBuilder filters(String countryCode, String search, Boolean active, Integer tier) {
        return new QueryFilterBuilder()
                .eq("q.iso2", "countryCode", countryCode)
                .ilike("LOWER(q.names ->> 'en') LIKE :search OR LOWER(q.names ->> 'ru') LIKE :search", "search", search)
                .bool("q.is_active", "active", active)
                .eqInt("q.tier", "tier", tier);
    }

    private String timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    private String json(SilhouettePhotoUpdate photo) {
        try {
            return mapper.writeValueAsString(photo);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode country photo " + photo.objectKey(), error);
        }
    }

    private List<SilhouetteHintPreviewDto> hints(String json) {
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, SilhouetteHintPreviewDto.class));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot decode silhouette question clues", error);
        }
    }
}
