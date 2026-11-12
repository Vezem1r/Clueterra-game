package io.clueterra.questions.image;

import io.clueterra.questions.storage.ImageStorageProperties;
import io.clueterra.questions.storage.ImageStorageService;
import io.clueterra.questions.storage.StoredImageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaGarbageCollectionService {

    private static final Pattern PREVIEW_KEY = Pattern.compile("^previews/[1-9][0-9]*/(.+)[.]jpg$");

    private final MediaGarbageCollectionRepository repository;
    private final Optional<ImageStorageService> imageStorageService;
    private final ImageStorageProperties storageProperties;
    private final Clock clock;

    public MediaGarbageCollectionResult collect() {
        if (imageStorageService.isEmpty()) {
            return new MediaGarbageCollectionResult(0, 0, 0, 0);
        }

        ImageStorageService storage = imageStorageService.orElseThrow();
        int registeredOrphans = repository.refreshOrphanState();
        int deletedRegistered = 0;
        int deletedUnregistered = 0;
        int failures = 0;

        for (ImageRenditionDeletionCandidate candidate
                : repository.findDeletionCandidates(storageProperties.getOrphanGraceHours())) {
            if (storage.deleteObject(candidate.storageBucket(), candidate.objectKey())) {
                repository.markDeleted(candidate.renditionId());
                deletedRegistered++;
            } else {
                failures++;
            }
        }

        Instant cutoff = Instant.now(clock)
                .minus(storageProperties.getOrphanGraceHours(), ChronoUnit.HOURS);
        for (String bucket : storageProperties.getManagedBuckets()) {
            SweepOutcome outcome = sweepUnregistered(storage, bucket, cutoff);
            deletedUnregistered += outcome.deleted();
            failures += outcome.failures();
        }

        log.info(
                "[MEDIA_GC] State refreshed={}, registered deleted={}, unregistered deleted={}, failures={}",
                registeredOrphans,
                deletedRegistered,
                deletedUnregistered,
                failures);
        return new MediaGarbageCollectionResult(
                registeredOrphans,
                deletedRegistered,
                deletedUnregistered,
                failures);
    }

    private SweepOutcome sweepUnregistered(ImageStorageService storage, String bucket, Instant cutoff) {
        try {
            storage.verifyBucketAccess(bucket);
        } catch (IllegalStateException exception) {
            log.error("[MEDIA_GC]", exception);
            return new SweepOutcome(0, 1);
        }

        Set<String> registeredKeys = repository.findRegisteredObjectKeys(bucket);
        List<StoredImageObject> objects = storage.listStoredObjects(bucket).stream()
                .filter(object -> object.lastModified() != null && object.lastModified().isBefore(cutoff))
                .toList();
        List<StoredImageObject> originals = objects.stream()
                .filter(object -> previewSource(object).isEmpty())
                .toList();
        List<StoredImageObject> unregistered = originals.stream()
                .filter(object -> !registeredKeys.contains(object.objectPath()))
                .toList();
        List<StoredImageObject> orphanPreviews = objects.stream()
                .filter(object -> previewSource(object).filter(source -> !registeredKeys.contains(source)).isPresent())
                .toList();
        if (unregistered.isEmpty() && orphanPreviews.isEmpty()) {
            return new SweepOutcome(0, 0);
        }

        double fraction = originals.isEmpty() ? 0 : (double) unregistered.size() / originals.size();
        if (registeredKeys.isEmpty() || fraction > storageProperties.getMaxUnregisteredSweepFraction()) {
            log.error("[MEDIA_GC] Refusing to sweep bucket {}: {} of {} original objects ({}%) look unregistered "
                            + "(registered keys: {}). The media registry is probably incomplete.",
                    bucket, unregistered.size(), originals.size(), Math.round(fraction * 100), registeredKeys.size());
            return new SweepOutcome(0, 1);
        }

        int deleted = 0;
        int failures = 0;
        for (StoredImageObject object : Stream.concat(unregistered.stream(), orphanPreviews.stream()).toList()) {
            if (storage.deleteObject(bucket, object.objectPath())) {
                deleted++;
            } else {
                failures++;
            }
        }
        return new SweepOutcome(deleted, failures);
    }

    private static Optional<String> previewSource(StoredImageObject object) {
        Matcher matcher = PREVIEW_KEY.matcher(object.objectPath());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
