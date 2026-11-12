package io.clueterra.api.admin.hints.image;

public record DownloadedWikimediaImage(
        byte[] bytes,
        String contentType,
        String extension) {
}
