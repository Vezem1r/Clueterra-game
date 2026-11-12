package io.clueterra.questions.rivergame.generation;

import io.clueterra.questions.storage.ImageStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RiverCatalogAssetStore {

    private static final String ASSET_PATH = "assets/";
    private static final String KEY_PATTERN = "river-game/[0-9a-f]{64}\\.webp";

    private final Optional<ImageStorageService> imageStorageService;

    public void verify(String objectKey) {
        try {
            loadVerifiedBytes(objectKey);
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot read bundled river map: " + objectKey, error);
        }
    }

    public long store(String objectKey) throws IOException {
        byte[] bytes = loadVerifiedBytes(objectKey);
        imageStorageService.orElseThrow(() ->
                        new IllegalStateException("River game requires the hint-images MinIO bucket"))
                .storeBytes(bytes, objectKey, "image/webp")
                .orElseThrow(() -> new IllegalStateException("Could not store river map in MinIO: " + objectKey));
        return bytes.length;
    }

    private byte[] loadVerifiedBytes(String objectKey) throws IOException {
        if (objectKey == null || !objectKey.matches(KEY_PATTERN)) {
            throw new IllegalArgumentException("Invalid river map key: " + objectKey);
        }
        ClassPathResource resource = new ClassPathResource(ASSET_PATH + objectKey);
        if (!resource.exists()) {
            throw new IllegalStateException("Bundled river map not found: " + objectKey);
        }
        byte[] bytes;
        try (InputStream input = resource.getInputStream()) {
            bytes = input.readAllBytes();
        }
        String actualHash;
        try {
            actualHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
        String expectedHash = objectKey.substring("river-game/".length(), objectKey.length() - 5);
        if (!actualHash.equals(expectedHash)) {
            throw new IllegalStateException("Bundled river map hash mismatch for " + objectKey
                    + ": classpath bytes are " + actualHash
                    + ". Rebuild Questions resources without filtering WebP files.");
        }
        return bytes;
    }
}
