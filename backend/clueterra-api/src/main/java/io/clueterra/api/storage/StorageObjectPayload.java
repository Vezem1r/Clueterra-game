package io.clueterra.api.storage;

public record StorageObjectPayload(
        byte[] bytes,
        String contentType) {
}
