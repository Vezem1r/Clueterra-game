package io.clueterra.api.exception;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameSqlState {

    ROUND_NOT_CURRENT("CT409", HttpStatus.CONFLICT, "This round has already moved on"),
    INVALID_ANSWER("CT400", HttpStatus.BAD_REQUEST, "Choose an answer from the list");

    private final String sqlState;
    private final HttpStatus status;
    private final String message;

    public static Optional<GameSqlState> from(DataAccessException exception) {
        return Optional.of(exception.getMostSpecificCause())
                .filter(SQLException.class::isInstance)
                .map(cause -> ((SQLException) cause).getSQLState())
                .flatMap(state -> Arrays.stream(values())
                        .filter(candidate -> candidate.sqlState.equals(state))
                        .findFirst());
    }
}
