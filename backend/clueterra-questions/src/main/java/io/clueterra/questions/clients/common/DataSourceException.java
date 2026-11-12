package io.clueterra.questions.clients.common;

import io.clueterra.questions.entity.enums.DataSource;
import lombok.Getter;

@Getter
public class DataSourceException extends RuntimeException {

    private final DataSource source;
    private final Integer httpStatus;

    public DataSourceException(DataSource source, String message, Throwable cause) {
        super(String.format("[%s] %s", source, message), cause);
        this.source = source;
        this.httpStatus = null;
    }

    public DataSourceException(DataSource source, String url, int httpStatus, String message) {
        super(String.format("[%s] HTTP %d at %s: %s", source, httpStatus, url, message));
        this.source = source;
        this.httpStatus = httpStatus;
    }

    public static DataSourceException parseError(DataSource source, String details, Throwable cause) {
        return new DataSourceException(source, "Parse error: " + details, cause);
    }
}
