package io.clueterra.api.admin.quizgame.repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.quizgame.dto.QuizContentSummaryDto;
import io.clueterra.api.admin.quizgame.dto.QuizQuestionPreviewDto;
import io.clueterra.api.admin.quizgame.dto.QuizStopPreviewDto;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.loader.SqlLoader;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QuizGeneratedContentJdbcRepository {

    private static final String FIND_STOPS = SqlLoader.load(QuizGeneratedContentJdbcRepository.class, "findStops");
    private static final String FIND_SUMMARY = SqlLoader.load(QuizGeneratedContentJdbcRepository.class, "findSummary");
    private static final String HAS_KIND = "EXISTS (SELECT 1 FROM cl_schema.quiz_game_question k "
            + "WHERE k.quiz_country_id = c.id AND k.kind = :kind)";
    private static final String NEIGHBOUR_SEPARATOR = ",";
    private static final Map<String, String> SORT_ORDER = Map.of(
            "COUNTRY", "c.names ->> 'en', c.id",
            "EASIEST", "c.tier, c.names ->> 'en', c.id",
            "HARDEST", "c.tier DESC, c.names ->> 'en', c.id",
            "MOST_PLAYED", "visits.visitors DESC, c.names ->> 'en', c.id",
            "LOWEST_ACCURACY", "questions.accuracy ASC NULLS LAST, c.names ->> 'en', c.id",
            "NEWEST", "c.generated_at DESC, c.id DESC");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public List<QuizStopPreviewDto> findStops(String countryCode, String search, Boolean active, Integer tier,
            String kind, String sort, int limit, int offset) {
        QueryFilterBuilder filter = new QueryFilterBuilder()
                .eq("c.iso2", "countryCode", countryCode)
                .ilike("LOWER(c.names ->> 'en') LIKE :search OR LOWER(c.names ->> 'ru') LIKE :search", "search", search)
                .bool("c.is_active", "active", active)
                .eqInt("c.tier", "tier", tier);
        Optional.of(kind).filter(value -> !value.isEmpty()).ifPresent(value -> filter.predicate(HAS_KIND));
        filter.params().addValue("kind", kind).addValue("limit", limit).addValue("offset", offset);
        String order = SORT_ORDER.getOrDefault(sort, SORT_ORDER.get("COUNTRY"));
        return jdbc.query(FIND_STOPS + filter.whereClause() + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                filter.params(),
                (rs, row) -> new QuizStopPreviewDto(
                        rs.getLong("id"), rs.getString("iso2"), rs.getString("name_en"), rs.getInt("tier"),
                        rs.getString("region"), rs.getString("subregion"), rs.getBoolean("is_active"),
                        neighbours(rs.getString("neighbours")), questions(rs.getString("questions")),
                        rs.getLong("visitors"), rs.getLong("explored")));
    }

    public QuizContentSummaryDto findSummary() {
        return jdbc.queryForObject(FIND_SUMMARY, Map.of(), (rs, row) -> new QuizContentSummaryDto(
                rs.getLong("active_countries"), rs.getLong("active_questions"), rs.getLong("players"),
                rs.getLong("explored"), rs.getLong("answers"), rs.getLong("correct")));
    }

    private List<String> neighbours(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : Arrays.stream(value.split(NEIGHBOUR_SEPARATOR)).map(String::trim).toList();
    }

    private List<QuizQuestionPreviewDto> questions(String json) {
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, QuizQuestionPreviewDto.class));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot decode quiz questions", error);
        }
    }
}
