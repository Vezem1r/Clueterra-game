package io.clueterra.api.assets.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLSession;

class StubByteArrayHttpResponse implements HttpResponse<byte[]> {

    private final int statusCode;
    private final HttpHeaders headers;
    private final byte[] body;

    StubByteArrayHttpResponse(int statusCode, String contentType, byte[] body) {
        this.statusCode = statusCode;
        this.headers = HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (name, value) -> true);
        this.body = body;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public HttpRequest request() {
        return null;
    }

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public byte[] body() {
        return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return Optional.empty();
    }

    @Override
    public URI uri() {
        return URI.create("https://example.com");
    }

    @Override
    public HttpClient.Version version() {
        return HttpClient.Version.HTTP_1_1;
    }
}
