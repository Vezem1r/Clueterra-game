package io.clueterra.questions.repository.jdbc;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.repository.CountryRepository;
import io.clueterra.questions.repository.util.SqlLoader;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CountryJdbcRepository implements CountryRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    private static final String INSERT_SQL = """
            INSERT INTO countries (id, iso_alpha2, iso_alpha3, name_en, name_native, region, subregion,
                capital, flag_emoji, flag_url, difficulty_modifier, is_active, created_at, updated_at)
            VALUES (:id, :isoAlpha2, :isoAlpha3, :nameEn, :nameNative, :region, :subregion,
                :capital, :flagEmoji, :flagUrl, :difficultyModifier, :isActive, :createdAt, :updatedAt)
            RETURNING *
            """;
    private static final String FILL_MISSING_FROM_SOURCE_SQL =
            SqlLoader.load(CountryJdbcRepository.class, "fillMissingFromSource");

    @Override
    public Country insert(Country country) {
        Instant now = Instant.now(clock);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", TSID.Factory.getTsid().toLong())
                .addValue("isoAlpha2", country.getIsoAlpha2())
                .addValue("isoAlpha3", country.getIsoAlpha3())
                .addValue("nameEn", country.getNameEn())
                .addValue("nameNative", country.getNameNative())
                .addValue("region", country.getRegion())
                .addValue("subregion", country.getSubregion())
                .addValue("capital", country.getCapital())
                .addValue("flagEmoji", country.getFlagEmoji())
                .addValue("flagUrl", country.getFlagUrl())
                .addValue("difficultyModifier", country.getDifficultyModifier())
                .addValue("isActive", country.getIsActive())
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(now));
        return jdbc.queryForObject(INSERT_SQL, params, rowMapper());
    }

    @Override
    public Country fillMissingFromSource(Country existing, Country incoming) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", existing.getId())
                .addValue("nameEn", incoming.getNameEn())
                .addValue("nameNative", incoming.getNameNative())
                .addValue("capital", incoming.getCapital())
                .addValue("flagEmoji", incoming.getFlagEmoji())
                .addValue("flagUrl", incoming.getFlagUrl())
                .addValue("updatedAt", Timestamp.from(Instant.now(clock)));
        return jdbc.query(FILL_MISSING_FROM_SOURCE_SQL, params, rowMapper()).stream()
                .findFirst()
                .orElse(existing);
    }

    @Override
    public void incrementTimesFeatured(Long countryId) {
        adjustTimesFeatured(countryId, 1);
    }

    @Override
    public void adjustTimesFeatured(Long countryId, int delta) {
        jdbc.update("""
                UPDATE countries
                SET times_featured = GREATEST(COALESCE(times_featured, 0) + :delta, 0), updated_at = :updatedAt
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", countryId)
                .addValue("delta", delta)
                .addValue("updatedAt", Timestamp.from(Instant.now(clock))));
    }

    @Override
    public Optional<Country> findByIsoAlpha2(String code) {
        List<Country> results = jdbc.query(
                "SELECT * FROM countries WHERE iso_alpha2 = :code",
                Map.of("code", code.trim().toUpperCase(Locale.ROOT)),
                rowMapper()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Optional<Country> findByIsoAlpha3(String code) {
        List<Country> results = jdbc.query(
                "SELECT * FROM countries WHERE iso_alpha3 = :code",
                Map.of("code", code.trim().toUpperCase(Locale.ROOT)),
                rowMapper()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Optional<Country> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        List<Country> results = jdbc.query(
                "SELECT * FROM countries WHERE id = :id",
                Map.of("id", id),
                rowMapper()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public List<Country> findByIsActiveTrue() {
        return jdbc.query(
                "SELECT * FROM countries WHERE is_active = true ORDER BY name_en",
                Map.of(),
                rowMapper()
        );
    }

    @Override
    public Map<Long, String> findAllCountryRegions() {
        return jdbc.query(
                "SELECT id, region FROM countries WHERE region IS NOT NULL",
                Map.of(),
                rs -> {
                    Map<Long, String> map = new HashMap<>();
                    while (rs.next()) {
                        map.put(rs.getLong("id"), rs.getString("region"));
                    }
                    return map;
                }
        );
    }

    @Override
    public long count() {
        Long result = jdbc.queryForObject("SELECT COUNT(*) FROM countries", Map.of(), Long.class);
        return result != null ? result : 0;
    }

    @Override
    public long countByIsActiveTrue() {
        Long result = jdbc.queryForObject(
                "SELECT COUNT(*) FROM countries WHERE is_active = true",
                Map.of(),
                Long.class
        );
        return result != null ? result : 0;
    }

    public List<Country> findAllActive() {
        return findByIsActiveTrue();
    }

    public long countActive() {
        return countByIsActiveTrue();
    }

    @Override
    public Optional<String> findNameById(Long id) {
        if (id == null) return Optional.empty();
        List<String> results = jdbc.query(
                "SELECT name_en FROM countries WHERE id = :id",
                Map.of("id", id),
                (rs, rowNum) -> rs.getString("name_en")
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Map<Long, String> findAllCountryNames() {
        return jdbc.query(
                "SELECT id, name_en FROM countries",
                Map.of(),
                rs -> {
                    Map<Long, String> map = new HashMap<>();
                    while (rs.next()) {
                        map.put(rs.getLong("id"), rs.getString("name_en"));
                    }
                    return map;
                }
        );
    }

    private RowMapper<Country> rowMapper() {
        return (rs, rowNum) -> mapRow(rs);
    }

    private Country mapRow(ResultSet rs) throws SQLException {
        return Country.builder()
                .id(rs.getLong("id"))
                .isoAlpha2(rs.getString("iso_alpha2"))
                .isoAlpha3(rs.getString("iso_alpha3"))
                .nameEn(rs.getString("name_en"))
                .nameNative(rs.getString("name_native"))
                .region(rs.getString("region"))
                .subregion(rs.getString("subregion"))
                .capital(rs.getString("capital"))
                .flagEmoji(rs.getString("flag_emoji"))
                .flagUrl(rs.getString("flag_url"))
                .difficultyModifier(rs.getBigDecimal("difficulty_modifier"))
                .isActive(rs.getBoolean("is_active"))
                .timesFeatured(getNullableInt(rs, "times_featured"))
                .timesGuessed(getNullableInt(rs, "times_guessed"))
                .successRate(rs.getBigDecimal("success_rate"))
                .createdAt(getInstant(rs, "created_at"))
                .updatedAt(getInstant(rs, "updated_at"))
                .build();
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }
}
