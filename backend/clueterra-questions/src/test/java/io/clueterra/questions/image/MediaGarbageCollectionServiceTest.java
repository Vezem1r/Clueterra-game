package io.clueterra.questions.image;

import io.clueterra.questions.storage.ImageStorageProperties;
import io.clueterra.questions.storage.ImageStorageService;
import io.clueterra.questions.storage.StoredImageObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaGarbageCollectionServiceTest {

    @Mock
    private MediaGarbageCollectionRepository repository;

    @Mock
    private ImageStorageService storage;

    private MediaGarbageCollectionService service;

    @BeforeEach
    void setUp() {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setOrphanGraceHours(24);
        properties.setManagedBuckets(List.of("hint-images"));
        properties.setMaxUnregisteredSweepFraction(0.5);
        service = new MediaGarbageCollectionService(repository, Optional.of(storage), properties, Clock.systemUTC());
    }

    @Test
    void deletesOnlyExpiredRegisteredAndUnregisteredObjects() {
        Instant now = Instant.now();
        ImageRenditionDeletionCandidate deletedCandidate =
                new ImageRenditionDeletionCandidate(11L, "hint-images", "retired/old.jpg");
        ImageRenditionDeletionCandidate failedCandidate =
                new ImageRenditionDeletionCandidate(12L, "budget-images", "retired/retry.jpg");
        when(repository.refreshOrphanState()).thenReturn(2);
        when(repository.findDeletionCandidates(24)).thenReturn(List.of(deletedCandidate, failedCandidate));
        when(storage.deleteObject("hint-images", "retired/old.jpg")).thenReturn(true);
        when(storage.deleteObject("budget-images", "retired/retry.jpg")).thenReturn(false);
        when(repository.findRegisteredObjectKeys("hint-images")).thenReturn(Set.of("kept/current.jpg"));
        when(storage.listStoredObjects("hint-images")).thenReturn(List.of(
                new StoredImageObject("kept/current.jpg", now.minus(10, ChronoUnit.DAYS)),
                new StoredImageObject("unregistered/old.jpg", now.minus(2, ChronoUnit.DAYS)),
                new StoredImageObject("unregistered/new.jpg", now.minus(1, ChronoUnit.HOURS)),
                new StoredImageObject("unregistered/unknown-age.jpg", null)));
        when(storage.deleteObject("hint-images", "unregistered/old.jpg")).thenReturn(true);

        MediaGarbageCollectionResult result = service.collect();

        assertThat(result).isEqualTo(new MediaGarbageCollectionResult(2, 1, 1, 1));
        verify(repository).markDeleted(11L);
        verify(repository, never()).markDeleted(12L);
        verify(storage, never()).deleteObject("hint-images", "kept/current.jpg");
        verify(storage, never()).deleteObject("hint-images", "unregistered/new.jpg");
        verify(storage, never()).deleteObject("hint-images", "unregistered/unknown-age.jpg");
    }

    @Test
    void skipsReconciliationWhenBucketPermissionsAreMissing() {
        when(repository.refreshOrphanState()).thenReturn(0);
        when(repository.findDeletionCandidates(24)).thenReturn(List.of());
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setOrphanGraceHours(24);
        properties.setManagedBuckets(List.of("budget-images"));
        service = new MediaGarbageCollectionService(repository, Optional.of(storage), properties, Clock.systemUTC());
        doThrow(new IllegalStateException("access denied"))
                .when(storage).verifyBucketAccess("budget-images");

        MediaGarbageCollectionResult result = service.collect();

        assertThat(result).isEqualTo(new MediaGarbageCollectionResult(0, 0, 0, 1));
        verify(storage, never()).listStoredObjects("budget-images");
    }

    @Test
    void keepsPreviewsOfRegisteredImagesAndDropsPreviewsWithoutASource() {
        Instant old = Instant.now().minus(5, ChronoUnit.DAYS);
        when(repository.refreshOrphanState()).thenReturn(0);
        when(repository.findDeletionCandidates(24)).thenReturn(List.of());
        when(repository.findRegisteredObjectKeys("hint-images")).thenReturn(Set.of("ai/fr/tower.jpg"));
        when(storage.listStoredObjects("hint-images")).thenReturn(List.of(
                new StoredImageObject("ai/fr/tower.jpg", old),
                new StoredImageObject("previews/480/ai/fr/tower.jpg.jpg", old),
                new StoredImageObject("previews/960/ai/fr/tower.jpg.jpg", old),
                new StoredImageObject("previews/1280/ai/fr/tower.jpg.jpg", old),
                new StoredImageObject("previews/480/ai/fr/retired.jpg.jpg", old)));
        when(storage.deleteObject("hint-images", "previews/480/ai/fr/retired.jpg.jpg")).thenReturn(true);

        MediaGarbageCollectionResult result = service.collect();

        assertThat(result).isEqualTo(new MediaGarbageCollectionResult(0, 0, 1, 0));
        verify(storage, never()).deleteObject("hint-images", "ai/fr/tower.jpg");
        verify(storage, never()).deleteObject("hint-images", "previews/960/ai/fr/tower.jpg.jpg");
    }

    @Test
    void refusesToSweepBucketWhenRegistryLooksEmpty() {
        Instant old = Instant.now().minus(5, ChronoUnit.DAYS);
        when(repository.refreshOrphanState()).thenReturn(0);
        when(repository.findDeletionCandidates(24)).thenReturn(List.of());
        when(repository.findRegisteredObjectKeys("hint-images")).thenReturn(Set.of());
        when(storage.listStoredObjects("hint-images")).thenReturn(List.of(
                new StoredImageObject("a.jpg", old),
                new StoredImageObject("b.jpg", old)));

        MediaGarbageCollectionResult result = service.collect();

        assertThat(result).isEqualTo(new MediaGarbageCollectionResult(0, 0, 0, 1));
        verify(storage, never()).deleteObject(eq("hint-images"), anyString());
    }

    @Test
    void refusesToSweepWhenTooLargeShareOfBucketLooksUnregistered() {
        Instant old = Instant.now().minus(5, ChronoUnit.DAYS);
        when(repository.refreshOrphanState()).thenReturn(0);
        when(repository.findDeletionCandidates(24)).thenReturn(List.of());
        when(repository.findRegisteredObjectKeys("hint-images")).thenReturn(Set.of("kept.jpg"));
        when(storage.listStoredObjects("hint-images")).thenReturn(List.of(
                new StoredImageObject("kept.jpg", old),
                new StoredImageObject("orphan-1.jpg", old),
                new StoredImageObject("orphan-2.jpg", old)));

        MediaGarbageCollectionResult result = service.collect();

        assertThat(result.failures()).isEqualTo(1);
        verify(storage, never()).deleteObject(eq("hint-images"), anyString());
    }
}
