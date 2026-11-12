package io.clueterra.api.admin.indicators.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ComboIndicatorConfigJdbcRepositoryTest {

    @Test
    void loadsSqlResourcesOnInitialization() {
        assertThatCode(() -> new ComboIndicatorConfigJdbcRepository(mock(NamedParameterJdbcTemplate.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void convertsPostgresBigintCountsToInteger() {
        assertThat(ComboIndicatorConfigJdbcRepository.toInteger(7L)).isEqualTo(7);
        assertThat(ComboIndicatorConfigJdbcRepository.toInteger(3)).isEqualTo(3);
        assertThat(ComboIndicatorConfigJdbcRepository.toInteger(null)).isNull();
    }
}
