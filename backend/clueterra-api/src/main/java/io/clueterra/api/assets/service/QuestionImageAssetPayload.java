package io.clueterra.api.assets.service;

public record QuestionImageAssetPayload(
        byte[] bytes,
        String contentType) {
}
