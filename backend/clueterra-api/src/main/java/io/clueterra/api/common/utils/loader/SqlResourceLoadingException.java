package io.clueterra.api.common.utils.loader;

public class SqlResourceLoadingException extends RuntimeException {

    public SqlResourceLoadingException(String message) {
        super(message);
    }

    public SqlResourceLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
