package io.clueterra.questions.clients.mapillary.repository;

import io.clueterra.questions.clients.mapillary.dto.MapillaryCapitalCoordinate;
import io.clueterra.questions.repository.util.SqlLoader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MapillaryCapitalCoordinateRepository {

    private static final String FIND_ALL_SQL =
            SqlLoader.load(MapillaryCapitalCoordinateRepository.class, "findAll");
    private static final String FIND_BY_COUNTRY_CODE_SQL =
            SqlLoader.load(MapillaryCapitalCoordinateRepository.class, "findByCountryCode");

    private final NamedParameterJdbcTemplate jdbc;

    public List<MapillaryCapitalCoordinate> findAll() {
        return jdbc.query(FIND_ALL_SQL, Map.of(), this::mapRow);
    }

    public Optional<MapillaryCapitalCoordinate> findByCountryCode(String countryCode) {
        return jdbc.query(
                        FIND_BY_COUNTRY_CODE_SQL,
                        Map.of("countryCode", countryCode),
                        this::mapRow)
                .stream()
                .findFirst();
    }

    private MapillaryCapitalCoordinate mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new MapillaryCapitalCoordinate(
                rs.getString("country_code"),
                rs.getString("capital_name"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"));
    }
}
