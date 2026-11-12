package io.clueterra.api.admin.hints.image;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class WikimediaImageException extends RuntimeException {

    private final HttpStatus status;

    public WikimediaImageException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public WikimediaImageException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
