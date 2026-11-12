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

import io.clueterra.api.silhouettegame.repository.SilhouetteGameJdbcRepository;
import io.clueterra.api.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/assets/countries")
@RequiredArgsConstructor
@Tag(name = "Country Assets", description = "Landmark photos shown on guessed countries")
public class CountryPhotoAssetController {

    private static final String PHOTO_FILE = "[0-9a-f]{64}\\.jpg";
    private static final String PHOTO_KEY_PREFIX = "country-photos/";
    private static final long CACHE_DAYS = 7;

    private final StorageService storageService;
    private final SilhouetteGameJdbcRepository silhouetteRepository;

    @GetMapping("/photos/{fileName:.+}")
    @Operation(summary = "Get the active Wikimedia Commons landmark photo of a country")
    public ResponseEntity<byte[]> getCountryPhoto(@PathVariable String fileName) {
        if (!fileName.matches(PHOTO_FILE)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid country photo path");
        }
        String objectKey = PHOTO_KEY_PREFIX + fileName;
        String mimeType = silhouetteRepository.findCountryPhotoMimeType(objectKey)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Country photo not found"));
        byte[] bytes = storageService.readFromHintBucket(objectKey)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Country photo not found"))
                .bytes();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(bytes.length)
                .cacheControl(CacheControl.maxAge(CACHE_DAYS, TimeUnit.DAYS).cachePublic())
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(bytes);
    }
}
