package io.clueterra.api.common.utils.database;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public class QueryFilterBuilder {

  private final StringBuilder where = new StringBuilder(" WHERE 1=1");
  private final MapSqlParameterSource params = new MapSqlParameterSource();

  public QueryFilterBuilder eq(String column, String paramName, String value) {
    if (value != null && !value.isBlank()) {
      where.append(" AND ").append(column).append(" = :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder eqLong(String column, String paramName, Long value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" = :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder eqInt(String column, String paramName, Integer value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" = :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder bool(String column, String paramName, Boolean value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" = :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder ilike(String columns, String paramName, String value) {
    if (value != null && !value.isBlank()) {
      where.append(" AND (").append(columns).append(")");
      params.addValue(paramName, "%" + value.toLowerCase() + "%");
    }
    return this;
  }

  public QueryFilterBuilder gte(String column, String paramName, String value, String cast) {
    if (value != null && !value.isBlank()) {
      where.append(" AND ").append(column).append(" >= :").append(paramName);
      if (cast != null) where.append("::").append(cast);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder lte(String column, String paramName, String value, String cast) {
    if (value != null && !value.isBlank()) {
      where.append(" AND ").append(column).append(" <= :").append(paramName);
      if (cast != null) where.append("::").append(cast);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder gteInt(String column, String paramName, Integer value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" >= :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder lteInt(String column, String paramName, Integer value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" <= :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder gteDecimal(String column, String paramName, BigDecimal value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" >= :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder lteDecimal(String column, String paramName, BigDecimal value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" <= :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder neq(String column, String paramName, String value) {
    if (value != null && !value.isBlank()) {
      where.append(" AND ").append(column).append(" != :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder gteDate(String column, String paramName, LocalDate value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" >= :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder lteDate(String column, String paramName, LocalDate value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" <= :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder neqDate(String column, String paramName, LocalDate value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" != :").append(paramName);
      params.addValue(paramName, value);
    }
    return this;
  }

  public QueryFilterBuilder notNull(String column, Boolean value) {
    if (value != null) {
      where.append(" AND ").append(column).append(value ? " IS NOT NULL" : " IS NULL");
    }
    return this;
  }

  public QueryFilterBuilder in(String column, String paramName, List<?> values) {
    if (values != null && !values.isEmpty()) {
      where.append(" AND ").append(column).append(" IN (:").append(paramName).append(")");
      params.addValue(paramName, values);
    }
    return this;
  }

  public void predicate(String trustedPredicate) {
    if (trustedPredicate != null && !trustedPredicate.isBlank()) {
      where.append(" AND (").append(trustedPredicate).append(")");
    }
  }

  public String whereClause() {
    return where.toString();
  }

  public MapSqlParameterSource params() {
    return params;
  }
}
