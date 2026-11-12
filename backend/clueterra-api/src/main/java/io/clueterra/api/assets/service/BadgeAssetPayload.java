package io.clueterra.api.assets.service;

public record BadgeAssetPayload(
        byte[] bytes,
        String contentType) {
}
