package io.clueterra.questions.clients.hydrorivers;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
public class HydroRiverSkeletonAssetService implements BundledImageAssetProvider {

    static final String ASSET_BASE_PATH = "assets/river-skeletons/";
    static final String OBJECT_BASE_PATH = "hydrorivers/river-skeletons/";
    static final String SVG_CONTENT_TYPE = "image/svg+xml";
    private static final String SOURCE_PAGE_URL = "https://www.hydrosheds.org/products/hydrorivers";
    private static final String LICENSE_URL =
            "https://data.hydrosheds.org/file/technical-documentation/HydroSHEDS_TechDoc_v1_4.pdf";
    private static final Set<String> EXCLUDED_COUNTRIES = Set.of("kw");
    private static final Map<String, Set<HydroRiverSkeletonVariant>> EXCLUDED_VARIANTS = Map.of("is", Set.of(HydroRiverSkeletonVariant.MEDIUM));

    private final Optional<ImageStorageService> imageStorageService;

    public boolean isAvailable() {
        return imageStorageService.isPresent() && !availableVariants("US").isEmpty();
    }

    public boolean canProvide(String isoAlpha2) {
        return imageStorageService.isPresent() && !availableVariants(isoAlpha2).isEmpty();
    }

    public List<HydroRiverSkeletonVariant> availableVariants(String isoAlpha2) {
        return normalizeIsoAlpha2(isoAlpha2)
                .map(code -> Arrays.stream(HydroRiverSkeletonVariant.values())
                        .filter(variant -> !EXCLUDED_COUNTRIES.contains(code))
                        .filter(variant -> !EXCLUDED_VARIANTS.getOrDefault(code, Set.of()).contains(variant))
                        .filter(variant -> resource(code, variant).exists())
                        .toList())
                .orElseGet(List::of);
    }

    public Optional<String> resolveObjectKey(String isoAlpha2, HydroRiverSkeletonVariant variant) {
        Optional<String> normalizedCode = normalizeIsoAlpha2(isoAlpha2);
        if (normalizedCode.isEmpty() || variant == null || imageStorageService.isEmpty()) {
            return Optional.empty();
        }

        String code = normalizedCode.get();
        if (EXCLUDED_COUNTRIES.contains(code) || EXCLUDED_VARIANTS.getOrDefault(code, Set.of()).contains(variant)) {
            return Optional.empty();
        }
        ClassPathResource resource = resource(code, variant);
        if (!resource.exists()) {
            return Optional.empty();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = FileCopyUtils.copyToByteArray(inputStream);
            return imageStorageService.get().storeBytes(
                    bytes,
                    objectKey(code, variant, bytes),
                    SVG_CONTENT_TYPE);
        } catch (IOException e) {
            log.warn("Failed to load bundled HydroRIVERS river skeleton for {} ({}): {}",
                    code, variant.assetSuffix(), e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> resolveObjectKeyForIndicator(String isoAlpha2, String sourceIndicator) {
        return HydroRiverSkeletonVariant.fromIndicator(sourceIndicator)
                .flatMap(variant -> resolveObjectKey(isoAlpha2, variant));
    }

    @Override
    public DataSource source() {
        return DataSource.HYDRO_RIVERS;
    }

    @Override
    public ImageMetadata metadata() {
        return new ImageMetadata(
                null,
                SOURCE_PAGE_URL,
                "HydroRIVERS and Natural Earth contributors",
                "River geometry: HydroRIVERS (Lehner & Grill, 2013). Boundaries and labels: Natural Earth.",
                "HydroSHEDS License Agreement; Natural Earth public domain",
                LICENSE_URL);
    }

    @Override
    public Optional<String> restoreObject(ImageAuditCandidate candidate) {
        return candidate == null
                ? Optional.empty()
                : resolveObjectKeyForIndicator(candidate.countryCode(), candidate.sourceIndicator());
    }

    @Override
    public String unavailableReason() {
        return "Bundled HydroRIVERS asset is unavailable";
    }

    private ClassPathResource resource(String code, HydroRiverSkeletonVariant variant) {
        return new ClassPathResource(ASSET_BASE_PATH + fileName(code, variant));
    }

    private String objectKey(String code, HydroRiverSkeletonVariant variant, byte[] bytes) {
        try {
            String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return OBJECT_BASE_PATH + code + "/" + variant.assetSuffix() + "-" + sha256 + ".svg";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String fileName(String code, HydroRiverSkeletonVariant variant) {
        return code + "-" + variant.assetSuffix() + ".svg";
    }

    private Optional<String> normalizeIsoAlpha2(String isoAlpha2) {
        if (isoAlpha2 == null || !isoAlpha2.matches("^[A-Za-z]{2}$")) {
            return Optional.empty();
        }
        return Optional.of(isoAlpha2.toLowerCase(Locale.ROOT));
    }
}
