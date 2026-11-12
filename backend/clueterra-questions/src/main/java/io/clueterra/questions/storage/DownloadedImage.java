package io.clueterra.questions.storage;

import java.net.http.HttpHeaders;

public record DownloadedImage(int statusCode, HttpHeaders headers, byte[] body, boolean tooLarge) {

    String contentType() {
        return headers.firstValue("Content-Type").orElse("image/jpeg");
    }

    boolean hasBody() {
        return body != null && body.length > 0;
    }
}
