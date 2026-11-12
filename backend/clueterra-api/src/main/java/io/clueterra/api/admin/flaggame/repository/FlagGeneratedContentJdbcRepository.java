package io.clueterra.api.admin.flaggame.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.flaggame.dto.FlagHintPreviewDto;
import io.clueterra.api.admin.flaggame.dto.FlagQuestionPreviewDto;
import io.clueterra.api.admin.flaggame.dto.FlagQuestionSummaryDto;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.loader.SqlLoader;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FlagGeneratedContentJdbcRepository {

    private static final String FIND_QUESTIONS =
            SqlLoader.load(FlagGeneratedContentJdbcRepository.class, "findQuestions");
    private static final String FIND_SUMMARY =
            SqlLoader.load(FlagGeneratedContentJdbcRepository.class, "findSummary");
    private static final String FLAG_URL = "/api/assets/flags/%s.svg";
    private static final String HAS_CAPITAL = "EXISTS (SELECT 1 FROM cl_schema.flag_game_hint h "
            + "WHERE h.question_id = q.id AND h.kind = 'CAPITAL')";
    private static final Map<String, String> CAPITAL_PREDICATES = Map.of(
            "WITH_CAPITAL", HAS_CAPITAL,
            "NO_CAPITAL", "NOT " + HAS_CAPITAL);
    private static final Map<String, String> SORT_ORDER = Map.of(
            "COUNTRY", "q.names ->> 'en', q.id",
            "EASIEST", "q.tier, q.names ->> 'en', q.id",
            "HARDEST", "q.tier DESC, q.names ->> 'en', q.id",
            "MOST_PLAYED", "stats.players DESC, q.names ->> 'en', q.id",
            "MOST_SKIPPED", "stats.skips DESC, q.names ->> 'en', q.id",
            "MOST_CONFUSED", "misses.wrong_guesses DESC, q.names ->> 'en', q.id",
            "NEWEST", "q.generated_at DESC, q.id DESC");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public List<FlagQuestionPreviewDto> findQuestions(String countryCode, String search, Boolean active, Integer tier,
            String capital, String sort, int limit, int offset) {
        QueryFilterBuilder filter = new QueryFilterBuilder()
                .eq("q.iso2", "countryCode", countryCode)
                .ilike("LOWER(q.names ->> 'en') LIKE :search OR LOWER(q.names ->> 'ru') LIKE :search", "search", search)
                .bool("q.is_active", "active", active)
                .eqInt("q.tier", "tier", tier);
        Optional.ofNullable(CAPITAL_PREDICATES.get(capital)).ifPresent(filter::predicate);
        filter.params().addValue("limit", limit).addValue("offset", offset);
        String order = SORT_ORDER.getOrDefault(sort, SORT_ORDER.get("COUNTRY"));
        return jdbc.query(FIND_QUESTIONS + filter.whereClause() + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                filter.params(),
                (rs, row) -> new FlagQuestionPreviewDto(
                        rs.getLong("id"), rs.getString("iso2"), rs.getString("name_en"), rs.getInt("tier"),
                        rs.getString("region"), rs.getString("subregion"), rs.getBoolean("is_active"),
                        FLAG_URL.formatted(rs.getString("iso2").toLowerCase(Locale.ROOT)), hints(rs.getString("hints")),
                        rs.getLong("players"), rs.getLong("solved"), rs.getLong("skips"), rs.getLong("reveals"),
                        rs.getLong("wrong_guesses"),
                        rs.getObject("average_hints") == null ? null : rs.getDouble("average_hints"),
                        timestamp(rs.getTimestamp("generated_at"))));
    }

    public FlagQuestionSummaryDto findSummary() {
        return jdbc.queryForObject(FIND_SUMMARY, Map.of(), (rs, row) -> new FlagQuestionSummaryDto(
                rs.getLong("active_questions"), rs.getLong("capital_clues"), rs.getLong("players"),
                rs.getLong("solves"), rs.getLong("guesses")));
    }

    private String timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    private List<FlagHintPreviewDto> hints(String json) {
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, FlagHintPreviewDto.class));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot decode flag question clues", error);
        }
    }
}
