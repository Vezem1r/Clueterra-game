package io.clueterra.api.assets.service;

public record FlagAssetPayload(
        byte[] bytes,
        String contentType) {
}
