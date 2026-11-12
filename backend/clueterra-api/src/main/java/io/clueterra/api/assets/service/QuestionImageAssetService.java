package io.clueterra.api.assets.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.clueterra.api.assets.repository.QuestionImageAssetJdbcRepository;
import io.clueterra.api.assets.repository.QuestionImageReference;
import io.clueterra.api.storage.StorageObjectPayload;
import io.clueterra.api.storage.StorageService;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionImageAssetService {

    private static final String MAPILLARY_SOURCE = "MAPILLARY";
    private static final Set<Integer> RESPONSIVE_IMAGE_WIDTHS = Set.of(480, 960, 1600);
    private static final String VECTOR_IMAGE_EXTENSION = ".svg";
    private static final Pattern BUNDLED_FLAG_PATH = Pattern.compile("^/api/assets/flags/([a-z]{2})\\.svg$");

    private final QuestionImageAssetJdbcRepository imageAssetRepository;
    private final StorageService storageService;
    private final MapillaryImageUrlResolver mapillaryImageUrlResolver;
    private final BudgetImageThumbnailService thumbnailService;
    private final FlagAssetService flagAssetService;

    public QuestionImageAssetPayload fetchHint(Long hintId) {
        return fetchHint(hintId, null);
    }

    public QuestionImageAssetPayload fetchHint(Long hintId, Integer width) {
        QuestionImageReference reference = imageAssetRepository.findHintImageReference(hintId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Hint image not found"));
        if (width != null && !RESPONSIVE_IMAGE_WIDTHS.contains(width)) {
            throw new ResponseStatusException(BAD_REQUEST, "Hint image width must be 480, 960 or 1600");
        }
        if (width == null || reference.imageUrl().endsWith(VECTOR_IMAGE_EXTENSION)) {
            return fetch(reference, "Hint image not found");
        }
        return fetchThumbnail(reference, width);
    }

    public QuestionImageAssetPayload fetchStatistic(Long statisticId) {
        QuestionImageReference reference = imageAssetRepository.findStatisticImageReference(statisticId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Statistic image not found"));
        return fetch(reference, "Statistic image not found");
    }

    private QuestionImageAssetPayload fetch(QuestionImageReference imageReference,
                                            String notFoundMessage) {
        String storedImageUrl = imageReference.imageUrl();
        Matcher flag = BUNDLED_FLAG_PATH.matcher(storedImageUrl);
        if (flag.matches()) {
            FlagAssetPayload payload = flagAssetService.fetchFlag(flag.group(1));
            return new QuestionImageAssetPayload(payload.bytes(), payload.contentType());
        }
        String objectPath = storageService.extractHintObjectKey(storedImageUrl);
        if (StringUtils.hasText(objectPath)) {
            return storageService.readFromHintBucket(objectPath)
                    .map(this::toPayload)
                    .orElseGet(() -> restoreAndBuild(objectPath, imageReference));
        }

        List<String> sourceUrls = resolveSourceUrls(imageReference);
        if (sourceUrls.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, notFoundMessage);
        }

        return downloadFirstAvailable(sourceUrls)
                .map(this::toPayload)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Question image is unavailable"));
    }

    private QuestionImageAssetPayload fetchThumbnail(QuestionImageReference reference, int width) {
        String objectPath = storageService.extractHintObjectKey(reference.imageUrl());
        String previewPath = previewObjectPath(objectPath, width);
        if (previewPath != null) {
            Optional<StorageObjectPayload> cached = storageService.readFromHintBucket(previewPath);
            if (cached.isPresent()) {
                return toPayload(cached.orElseThrow());
            }
        }

        try {
            BudgetAssetPayload thumbnail = thumbnailService.createWidthVariant(
                    () -> toBudgetPayload(fetch(reference, "Hint image not found")),
                    width);
            if (previewPath != null) {
                storeThumbnail(previewPath, thumbnail);
            }
            return new QuestionImageAssetPayload(thumbnail.bytes(), thumbnail.contentType());
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to create hint image preview for {} at width {}: {}",
                    objectPath,
                    width,
                    exception.getMessage());
            return fetch(reference, "Hint image not found");
        }
    }

    private void storeThumbnail(String objectPath, BudgetAssetPayload thumbnail) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(thumbnail.bytes())) {
            storageService.uploadToHintBucket(
                    objectPath,
                    inputStream,
                    thumbnail.bytes().length,
                    thumbnail.contentType());
        } catch (RuntimeException exception) {
            log.warn("Failed to cache hint image preview {}: {}", objectPath, exception.getMessage());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close hint image preview stream", exception);
        }
    }

    private String previewObjectPath(String objectPath, int width) {
        return StringUtils.hasText(objectPath)
                ? "previews/" + width + "/" + objectPath + ".jpg"
                : null;
    }

    private BudgetAssetPayload toBudgetPayload(QuestionImageAssetPayload payload) {
        return new BudgetAssetPayload(payload.bytes(), payload.contentType());
    }

    private QuestionImageAssetPayload restoreAndBuild(String objectPath,
                                                       QuestionImageReference imageReference) {
        List<String> sourceUrls = resolveSourceUrls(imageReference);
        if (sourceUrls.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Question image source not found");
        }

        return downloadStoreAndBuild(
                objectPath,
                sourceUrls
        );
    }

    private QuestionImageAssetPayload downloadStoreAndBuild(String objectPath,
                                                             List<String> sourceUrls) {
        StorageObjectPayload payload = downloadFirstAvailable(sourceUrls)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Question image could not be restored"));

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(payload.bytes())) {
            storageService.uploadToHintBucket(objectPath, inputStream, payload.bytes().length, payload.contentType());
        } catch (RuntimeException e) {
            log.warn("Failed to persist restored question image {}: {}", objectPath, e.getMessage());
        } catch (IOException e) {
            log.warn("Failed to close restored question image stream {}: {}", objectPath, e.getMessage());
        }

        return toPayload(payload);
    }

    private Optional<StorageObjectPayload> downloadFirstAvailable(List<String> sourceUrls) {
        return sourceUrls.stream()
                .map(storageService::downloadRemoteImage)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private List<String> resolveSourceUrls(QuestionImageReference imageReference) {
        List<String> sourceUrls = new ArrayList<>();
        if (MAPILLARY_SOURCE.equals(imageReference.source())) {
            mapillaryImageUrlResolver.resolveFreshImageUrl(imageReference.imageSourcePageUrl())
                    .ifPresent(sourceUrls::add);
        }
        addExternalImageUrl(sourceUrls, imageReference.originalImageUrl());
        addExternalImageUrl(sourceUrls, imageReference.imageUrl());
        return sourceUrls;
    }

    private void addExternalImageUrl(List<String> sourceUrls, String imageUrl) {
        if (StringUtils.hasText(imageUrl)
                && storageService.extractHintObjectKey(imageUrl) == null
                && !sourceUrls.contains(imageUrl)) {
            sourceUrls.add(imageUrl);
        }
    }

    private QuestionImageAssetPayload toPayload(StorageObjectPayload payload) {
        return new QuestionImageAssetPayload(payload.bytes(), payload.contentType());
    }
}
