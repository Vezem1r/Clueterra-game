package io.clueterra.api.assets.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.clueterra.api.rivergame.repository.RiverGameJdbcRepository;
import io.clueterra.api.storage.StorageObjectPayload;
import io.clueterra.api.storage.StorageService;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.Mockito;

class RiverGameAssetControllerTest {

    private final StorageService storageService = Mockito.mock(StorageService.class);
    private final RiverGameJdbcRepository repository = Mockito.mock(RiverGameJdbcRepository.class);
    private final RiverGameAssetController controller = new RiverGameAssetController(storageService, repository);

    @Test
    void publishesImmutableWebpFromHintBucket() {
        byte[] webp = "RIFF....WEBP".getBytes(StandardCharsets.US_ASCII);
        String fileName = "0".repeat(64) + ".webp";
        when(storageService.readFromHintBucket("river-game/" + fileName))
                .thenReturn(Optional.of(new StorageObjectPayload(webp, "image/webp")));

        ResponseEntity<byte[]> response = controller.getRiverMap(fileName);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("image/webp"));
        assertThat(response.getHeaders().getCacheControl()).contains("immutable");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(webp.length);
        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).contains("sandbox");
        assertThat(response.getHeaders().getFirst("Cross-Origin-Resource-Policy")).isEqualTo("same-origin");
        assertThat(response.getBody()).isEqualTo(webp);
        verify(storageService).readFromHintBucket("river-game/" + fileName);
    }

    @Test
    void rejectsMalformedAssetPathBeforeReadingBucket() {
        assertThatThrownBy(() -> controller.getRiverMap("q123-plain.svg"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void publishesActiveRiverPhotoWithItsStoredType() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        String fileName = "a".repeat(64) + ".jpg";
        when(repository.findRiverPhotoMimeType("river-photos/" + fileName)).thenReturn(Optional.of("image/jpeg"));
        when(storageService.readFromHintBucket("river-photos/" + fileName))
                .thenReturn(Optional.of(new StorageObjectPayload(jpeg, "image/jpeg")));

        ResponseEntity<byte[]> response = controller.getRiverPhoto(fileName);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getHeaders().getCacheControl()).doesNotContain("immutable");
        assertThat(response.getBody()).isEqualTo(jpeg);
    }

    @Test
    void hidesQuarantinedOrUnknownRiverPhotos() {
        String fileName = "b".repeat(64) + ".jpg";
        when(repository.findRiverPhotoMimeType("river-photos/" + fileName)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getRiverPhoto(fileName)).isInstanceOf(ResponseStatusException.class);
        verify(storageService, never()).readFromHintBucket(anyString());
    }

    @Test
    void rejectsMalformedPhotoPathBeforeQueryingMedia() {
        assertThatThrownBy(() -> controller.getRiverPhoto("Rhine.jpg")).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).findRiverPhotoMimeType(anyString());
    }
}
