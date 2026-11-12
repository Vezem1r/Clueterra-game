package io.clueterra.questions.countrycatalog.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.questions.countrycatalog.model.CatalogStatistic;
import io.clueterra.questions.countrycatalog.model.CatalogStatisticRow;
import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CountryCatalogRepository {

    private static final String FIND_STATISTICS = SqlLoader.load(CountryCatalogRepository.class, "findStatistics");

    private final NamedParameterJdbcTemplate jdbc;

    public Set<String> findMissingCountryCodes(Set<String> countryCodes) {
        Set<String> missing = new TreeSet<>(countryCodes);
        jdbc.queryForList("SELECT iso_alpha2 FROM cl_schema.countries WHERE iso_alpha2 IN (:codes)",
                new MapSqlParameterSource("codes", countryCodes), String.class).forEach(missing::remove);
        return missing;
    }

    public Map<String, List<CatalogStatistic>> findStatistics(Set<String> countryCodes) {
        return jdbc.query(FIND_STATISTICS, new MapSqlParameterSource("codes", countryCodes),
                        (rs, row) -> new CatalogStatisticRow(rs.getString("iso_alpha2"), new CatalogStatistic(
                                rs.getString("source"), rs.getString("indicator_code"),
                                rs.getObject("value") == null ? null : rs.getDouble("value"),
                                rs.getString("value_text"),
                                rs.getObject("year") == null ? null : rs.getInt("year"))))
                .stream()
                .collect(Collectors.groupingBy(CatalogStatisticRow::iso2,
                        Collectors.mapping(CatalogStatisticRow::statistic, Collectors.toList())));
    }
}
