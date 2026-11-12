package io.clueterra.api.assets.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.assets.repository.BudgetAssetJdbcRepository;
import io.clueterra.api.assets.repository.BudgetImageReference;
import io.clueterra.api.assets.repository.BudgetImageRole;
import io.clueterra.api.storage.StorageObjectPayload;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetAssetService {

    private static final Set<Integer> RESPONSIVE_IMAGE_DIMENSIONS = Set.of(480, 960, 1600);

    private final BudgetAssetJdbcRepository repository;
    private final StorageService storageService;
    private final BudgetImageThumbnailService thumbnailService;

    public BudgetAssetPayload fetchCandidateImage(
            String wikidataId, String category, String sourceSha1) {
        return fetchCandidateImage(wikidataId, category, sourceSha1, false);
    }

    public BudgetAssetPayload fetchCandidateImage(
            String wikidataId, String category, String sourceSha1, boolean thumbnail) {
        BudgetImageReference reference =
                repository
                        .findCandidateImageReference(wikidataId, category, sourceSha1)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Budget image not found"));
        return thumbnail ? fetchThumbnail(reference) : fetch(reference);
    }

    public BudgetAssetPayload fetchVariantImage(long variantId, String roleValue) {
        return fetchVariantImage(variantId, roleValue, null);
    }

    public BudgetAssetPayload fetchVariantImage(
            long variantId, String roleValue, Integer maximumDimension) {
        BudgetImageRole role = BudgetImageRole.fromPath(roleValue);
        BudgetImageReference reference = repository
                .findVariantImageReference(variantId, role)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Budget image not found"));
        if (maximumDimension == null) {
            return fetch(reference);
        }
        if (!RESPONSIVE_IMAGE_DIMENSIONS.contains(maximumDimension)) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Budget image width must be 480, 960 or 1600");
        }
        return fetchThumbnail(reference, maximumDimension);
    }

    public BudgetAssetPayload fetchAdminVariantImage(
            long variantId, String roleValue, boolean thumbnail) {
        BudgetImageRole role = BudgetImageRole.fromPath(roleValue);
        BudgetImageReference reference = repository
                .findAdminVariantImageReference(variantId, role)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Budget image not found"));
        return thumbnail ? fetchThumbnail(reference) : fetch(reference);
    }

    public BudgetAssetPayload fetchPublishedProjectImage(String wikidataId, String sourceSha1) {
        return fetchPublishedProjectImage(wikidataId, sourceSha1, null);
    }

    public BudgetAssetPayload fetchPublishedProjectImage(String wikidataId, String sourceSha1, Integer maximumDimension) {
        if (maximumDimension != null && !RESPONSIVE_IMAGE_DIMENSIONS.contains(maximumDimension)) {
            throw new ResponseStatusException(BAD_REQUEST, "Budget image width must be 480, 960 or 1600");
        }
        BudgetImageReference reference = repository
                .findPublishedProjectImageReference(wikidataId, sourceSha1)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Budget image not found"));
        return maximumDimension == null ? fetchThumbnail(reference) : fetchThumbnail(reference, maximumDimension);
    }

    private BudgetAssetPayload fetchThumbnail(BudgetImageReference reference) {
        return fetchThumbnail(reference, thumbnailService.maximumDimension(), false);
    }

    private BudgetAssetPayload fetchThumbnail(
            BudgetImageReference reference, int maximumDimension) {
        return fetchThumbnail(reference, maximumDimension, true);
    }

    private BudgetAssetPayload fetchThumbnail(
            BudgetImageReference reference, int maximumDimension, boolean widthVariant) {
        String previewKey = previewObjectKey(reference.objectKey(), maximumDimension);
        if (previewKey != null) {
            Optional<StorageObjectPayload> cached = storageService.readFromBudgetBucket(previewKey);
            if (cached.isPresent()) {
                return toPayload(cached.orElseThrow());
            }
        }

        try {
            BudgetAssetPayload thumbnail = widthVariant
                    ? thumbnailService.createWidthVariant(() -> fetch(reference), maximumDimension)
                    : thumbnailService.create(() -> fetch(reference));
            if (previewKey != null) {
                storeThumbnail(previewKey, thumbnail);
            }
            return thumbnail;
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to create BudgetGuessr thumbnail for {}: {}",
                    reference.objectKey(),
                    exception.getMessage());
            if (widthVariant) {
                throw new ResponseStatusException(
                        BAD_GATEWAY, "Failed to create responsive budget image", exception);
            }
            return fetch(reference);
        }
    }

    private void storeThumbnail(String objectKey, BudgetAssetPayload thumbnail) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(thumbnail.bytes())) {
            storageService.uploadToBudgetBucket(
                    objectKey, input, thumbnail.bytes().length, thumbnail.contentType());
        } catch (RuntimeException exception) {
            log.warn("Failed to cache BudgetGuessr thumbnail {}: {}", objectKey, exception.getMessage());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to cache budget thumbnail", exception);
        }
    }

    private String previewObjectKey(String objectKey, int maximumDimension) {
        return StringUtils.hasText(objectKey)
                ? "previews/" + maximumDimension + "/" + objectKey + ".jpg"
                : null;
    }

    private BudgetAssetPayload fetch(BudgetImageReference reference) {
        String objectKey = resolveObjectKey(reference);
        if (StringUtils.hasText(objectKey)) {
            return storageService
                    .readFromBudgetBucket(objectKey)
                    .map(this::toPayload)
                    .orElseGet(() -> restore(objectKey, reference));
        }
        return download(reference);
    }

    private BudgetAssetPayload restore(String objectKey, BudgetImageReference reference) {
        StorageObjectPayload payload = downloadPayload(reference);
        try (ByteArrayInputStream input = new ByteArrayInputStream(payload.bytes())) {
            storageService.uploadToBudgetBucket(
                    objectKey, input, payload.bytes().length, payload.contentType());
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to persist restored BudgetGuessr image {}: {}",
                    objectKey,
                    exception.getMessage());
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to restore budget image");
        }
        return toPayload(payload);
    }

    private BudgetAssetPayload download(BudgetImageReference reference) {
        return toPayload(downloadPayload(reference));
    }

    private StorageObjectPayload downloadPayload(BudgetImageReference reference) {
        return Stream.of(reference.fallbackImageUrl(), reference.originalImageUrl())
                .filter(StringUtils::hasText)
                .filter(this::isTrustedWikimediaUrl)
                .distinct()
                .map(storageService::downloadRemoteImage)
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_GATEWAY, "Failed to load budget image"));
    }

    private String resolveObjectKey(BudgetImageReference reference) {
        return reference.objectKey();
    }

    private boolean isTrustedWikimediaUrl(String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "upload.wikimedia.org".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private BudgetAssetPayload toPayload(StorageObjectPayload payload) {
        return new BudgetAssetPayload(payload.bytes(), payload.contentType());
    }
}
