package io.clueterra.api.assets.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.rivergame.repository.RiverGameJdbcRepository;
import io.clueterra.api.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/assets/rivers")
@RequiredArgsConstructor
@Tag(name = "River Game Assets", description = "Immutable river map assets and answer photos")
public class RiverGameAssetController {

    private static final String MAP_FILE = "[0-9a-f]{64}\\.webp";
    private static final String PHOTO_FILE = "[0-9a-f]{64}\\.jpg";
    private static final String PHOTO_KEY_PREFIX = "river-photos/";

    private final StorageService storageService;
    private final RiverGameJdbcRepository riverGameRepository;

    @GetMapping("/{fileName:.+}")
    @Operation(summary = "Get a versioned river map WebP")
    public ResponseEntity<byte[]> getRiverMap(@PathVariable String fileName) {
        if (!fileName.matches(MAP_FILE)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid river map path");
        }
        byte[] bytes = storageService.readFromHintBucket("river-game/" + fileName)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "River map not found"))
                .bytes();
        return image(bytes, "image/webp", CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }

    @GetMapping("/photos/{fileName:.+}")
    @Operation(summary = "Get an active Wikimedia Commons photo shown after a river is answered")
    public ResponseEntity<byte[]> getRiverPhoto(@PathVariable String fileName) {
        if (!fileName.matches(PHOTO_FILE)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid river photo path");
        }
        String objectKey = PHOTO_KEY_PREFIX + fileName;
        String mimeType = riverGameRepository.findRiverPhotoMimeType(objectKey)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "River photo not found"));
        byte[] bytes = storageService.readFromHintBucket(objectKey)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "River photo not found"))
                .bytes();
        return image(bytes, mimeType, CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic());
    }

    private ResponseEntity<byte[]> image(byte[] bytes, String mimeType, CacheControl cacheControl) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(bytes.length)
                .cacheControl(cacheControl)
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(bytes);
    }
}
