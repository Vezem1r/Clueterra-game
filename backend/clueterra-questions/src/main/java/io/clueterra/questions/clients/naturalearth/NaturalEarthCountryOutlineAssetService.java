package io.clueterra.questions.clients.naturalearth;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.image.BundledImageAssetProvider;
import io.clueterra.questions.image.ImageAuditCandidate;
import io.clueterra.questions.image.ImageMetadata;
import io.clueterra.questions.storage.ImageStorageService;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NaturalEarthCountryOutlineAssetService implements BundledImageAssetProvider {

    static final String ASSET_BASE_PATH = "assets/country-outlines/";
    static final String OBJECT_BASE_PATH = "natural-earth/";
    static final String SVG_CONTENT_TYPE = "image/svg+xml";
    private static final String SOURCE_PAGE_URL =
            "https://www.naturalearthdata.com/downloads/10m-cultural-vectors/10m-admin-0-details/";
    private static final String LICENSE_URL = "https://www.naturalearthdata.com/about/terms-of-use/";

    private final Optional<ImageStorageService> imageStorageService;

    public boolean isAvailable() {
        return imageStorageService.isPresent()
                && new ClassPathResource(ASSET_BASE_PATH + "us.svg").exists();
    }

    public boolean canProvide(String isoAlpha2) {
        return imageStorageService.isPresent()
                && normalizeIsoAlpha2(isoAlpha2)
                .map(code -> resource(code).exists())
                .orElse(false);
    }

    public Optional<String> resolveObjectKey(String isoAlpha2) {
        Optional<String> normalizedCode = normalizeIsoAlpha2(isoAlpha2);
        if (normalizedCode.isEmpty() || imageStorageService.isEmpty()) {
            return Optional.empty();
        }

        String code = normalizedCode.get();
        ClassPathResource resource = resource(code);
        if (!resource.exists()) {
            return Optional.empty();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = FileCopyUtils.copyToByteArray(inputStream);
            return imageStorageService.get().storeBytes(
                    bytes,
                    objectKey(code, bytes),
                    SVG_CONTENT_TYPE);
        } catch (IOException e) {
            log.warn("Failed to load bundled Natural Earth outline for {}", code, e);
            return Optional.empty();
        }
    }

    @Override
    public DataSource source() {
        return DataSource.NATURAL_EARTH;
    }

    @Override
    public ImageMetadata metadata() {
        return new ImageMetadata(
                null,
                SOURCE_PAGE_URL,
                "Natural Earth",
                "Made with Natural Earth.",
                "Public domain",
                LICENSE_URL);
    }

    @Override
    public Optional<String> restoreObject(ImageAuditCandidate candidate) {
        return candidate == null ? Optional.empty() : resolveObjectKey(candidate.countryCode());
    }

    @Override
    public String unavailableReason() {
        return "Bundled Natural Earth outline is unavailable";
    }

    private String objectKey(String code, byte[] bytes) {
        try {
            String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return OBJECT_BASE_PATH + code + "/" + sha256 + ".svg";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private ClassPathResource resource(String code) {
        return new ClassPathResource(ASSET_BASE_PATH + code + ".svg");
    }

    private Optional<String> normalizeIsoAlpha2(String isoAlpha2) {
        if (isoAlpha2 == null || !isoAlpha2.matches("^[A-Za-z]{2}$")) {
            return Optional.empty();
        }
        return Optional.of(isoAlpha2.toLowerCase(Locale.ROOT));
    }
}
