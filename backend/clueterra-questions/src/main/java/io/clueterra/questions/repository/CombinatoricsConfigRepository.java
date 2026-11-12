package io.clueterra.questions.repository;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.util.SqlLoader;
import io.clueterra.questions.service.hint.combinatorics.dto.ComboHintComponent;
import io.clueterra.questions.service.hint.combinatorics.dto.ComboHintConfigRecord;
import io.clueterra.questions.service.hint.combinatorics.dto.ComboHintDefinition;
import io.clueterra.questions.service.hint.combinatorics.dto.ComboHintPredicate;
import io.clueterra.questions.service.hint.combinatorics.dto.ComboPredicateMode;
import io.clueterra.questions.service.hint.combinatorics.dto.ComboPredicateOperator;
import io.clueterra.questions.service.hint.combinatorics.dto.ComboTextVariant;
import io.clueterra.questions.service.hint.combinatorics.dto.ComboValueFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CombinatoricsConfigRepository {

    private static final String FIND_ACTIVE_CONFIGS_SQL = SqlLoader.load(
            CombinatoricsConfigRepository.class,
            "findActiveConfigs"
    );
    private static final String FIND_ACTIVE_COMPONENTS_SQL = SqlLoader.load(
            CombinatoricsConfigRepository.class,
            "findActiveComponents"
    );
    private static final String FIND_ACTIVE_PREDICATES_SQL = SqlLoader.load(
            CombinatoricsConfigRepository.class,
            "findActivePredicates"
    );
    private static final String FIND_ACTIVE_VARIANTS_SQL = SqlLoader.load(
            CombinatoricsConfigRepository.class,
            "findActiveVariants"
    );

    private final NamedParameterJdbcTemplate jdbc;

    public List<ComboHintDefinition> findActiveDefinitions() {
        Map<String, List<ComboHintComponent>> componentsByIndicator = jdbc.query(
                        FIND_ACTIVE_COMPONENTS_SQL,
                        Map.of(),
                        componentRowMapper()
                ).stream()
                .collect(Collectors.groupingBy(
                        ComboHintComponent::indicatorCode,
                        Collectors.toList()
                ));

        Map<String, List<ComboHintPredicate>> predicatesByIndicator = jdbc.query(
                        FIND_ACTIVE_PREDICATES_SQL,
                        Map.of(),
                        predicateRowMapper()
                ).stream()
                .collect(Collectors.groupingBy(
                        ComboHintPredicate::indicatorCode,
                        Collectors.toList()
                ));

        Map<String, List<ComboTextVariant>> variantsByIndicator = jdbc.query(
                        FIND_ACTIVE_VARIANTS_SQL,
                        Map.of(),
                        variantRowMapper()
                ).stream()
                .collect(Collectors.groupingBy(
                        ComboTextVariant::indicatorCode,
                        Collectors.toList()
                ));

        return jdbc.query(FIND_ACTIVE_CONFIGS_SQL, Map.of(), configRowMapper()).stream()
                .map(config -> new ComboHintDefinition(
                        config.indicatorCode(),
                        config.displayName(),
                        config.category(),
                        config.hintType(),
                        config.familyType(),
                        config.baseDifficulty(),
                        config.baseQuality(),
                        config.maxAmbiguityCount(),
                        List.copyOf(componentsByIndicator.getOrDefault(config.indicatorCode(), List.of())),
                        List.copyOf(predicatesByIndicator.getOrDefault(config.indicatorCode(), List.of())),
                        List.copyOf(variantsByIndicator.getOrDefault(config.indicatorCode(), List.of()))
                ))
                .filter(definition -> !definition.components().isEmpty())
                .filter(definition -> !definition.variants().isEmpty())
                .toList();
    }

    private RowMapper<ComboHintConfigRecord> configRowMapper() {
        return (rs, rowNum) -> mapConfigRow(rs);
    }

    private RowMapper<ComboHintComponent> componentRowMapper() {
        return (rs, rowNum) -> new ComboHintComponent(
                rs.getString("indicator_code"),
                rs.getString("component_key"),
                DataSource.valueOf(rs.getString("stat_source")),
                rs.getString("stat_indicator_code"),
                ComboValueFormatter.valueOf(rs.getString("formatter")),
                rs.getBoolean("required")
        );
    }

    private RowMapper<ComboHintPredicate> predicateRowMapper() {
        return (rs, rowNum) -> new ComboHintPredicate(
                rs.getString("indicator_code"),
                rs.getInt("predicate_order"),
                rs.getString("left_component_key"),
                ComboPredicateOperator.valueOf(rs.getString("operator")),
                ComboPredicateMode.valueOf(rs.getString("comparison_mode")),
                rs.getBigDecimal("right_value_numeric"),
                rs.getString("right_component_key")
        );
    }

    private RowMapper<ComboTextVariant> variantRowMapper() {
        return (rs, rowNum) -> new ComboTextVariant(
                rs.getString("indicator_code"),
                rs.getInt("variant_no"),
                rs.getString("template")
        );
    }

    private ComboHintConfigRecord mapConfigRow(ResultSet rs) throws SQLException {
        return new ComboHintConfigRecord(
                rs.getString("indicator_code"),
                rs.getString("display_name"),
                Category.valueOf(rs.getString("category")),
                HintType.valueOf(rs.getString("hint_type")),
                rs.getString("family_type"),
                rs.getInt("base_difficulty"),
                rs.getBigDecimal("quality_score"),
                getNullableInt(rs, "max_ambiguity_count")
        );
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
