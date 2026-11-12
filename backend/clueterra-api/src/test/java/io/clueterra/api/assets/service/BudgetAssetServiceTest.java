package io.clueterra.api.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.assets.repository.BudgetAssetJdbcRepository;
import io.clueterra.api.assets.repository.BudgetImageReference;
import io.clueterra.api.assets.repository.BudgetImageRole;
import io.clueterra.api.storage.StorageObjectPayload;
import io.clueterra.api.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class BudgetAssetServiceTest {

  @Mock private BudgetAssetJdbcRepository repository;
  @Mock private StorageService storageService;
  @Mock private BudgetImageThumbnailService thumbnailService;

  private BudgetAssetService service;

  @BeforeEach
  void setUp() {
    service = new BudgetAssetService(repository, storageService, thumbnailService);
  }

  @Test
  void servesCandidateImageFromManagedBucket() {
    BudgetImageReference reference =
        new BudgetImageReference(
            "candidates/q1.jpg",
            "https://upload.wikimedia.org/thumb.jpg",
            "https://upload.wikimedia.org/original.jpg");
    StorageObjectPayload stored = new StorageObjectPayload(new byte[] {1, 2, 3}, "image/jpeg");
    when(repository.findCandidateImageReference("Q1", "BRIDGE", "sha1"))
        .thenReturn(Optional.of(reference));
    when(storageService.readFromBudgetBucket("candidates/q1.jpg")).thenReturn(Optional.of(stored));

    BudgetAssetPayload result = service.fetchCandidateImage("Q1", "BRIDGE", "sha1");

    assertThat(result.bytes()).containsExactly(1, 2, 3);
    assertThat(result.contentType()).isEqualTo("image/jpeg");
  }

  @Test
  void restoresMissingVariantImageFromWikimediaFallback() {
    BudgetImageReference reference =
        new BudgetImageReference(
            "candidates/q2.jpg",
            "https://upload.wikimedia.org/thumb.jpg",
            "https://upload.wikimedia.org/original.jpg");
    StorageObjectPayload downloaded = new StorageObjectPayload(new byte[] {4, 5}, "image/jpeg");
    when(repository.findVariantImageReference(2L, BudgetImageRole.PRIMARY))
        .thenReturn(Optional.of(reference));
    when(storageService.readFromBudgetBucket("candidates/q2.jpg")).thenReturn(Optional.empty());
    when(storageService.downloadRemoteImage(reference.fallbackImageUrl()))
        .thenReturn(Optional.of(downloaded));

    BudgetAssetPayload result = service.fetchVariantImage(2L, "primary");

    assertThat(result.bytes()).containsExactly(4, 5);
    verify(storageService)
        .uploadToBudgetBucket(
            eq("candidates/q2.jpg"), any(InputStream.class), eq(2L), eq("image/jpeg"));
  }

  @Test
  void createsHighResolutionThumbnailWithoutUsingLegacyPreviewCache() {
    BudgetImageReference reference =
        new BudgetImageReference(
            "candidates/q3.jpg",
            "https://upload.wikimedia.org/thumb.jpg",
            "https://upload.wikimedia.org/original.jpg");
    BudgetAssetPayload thumbnail = new BudgetAssetPayload(new byte[] {7, 8, 9}, "image/jpeg");
    when(repository.findCandidateImageReference("Q3", "SKYSCRAPER", "sha3"))
        .thenReturn(Optional.of(reference));
    when(thumbnailService.maximumDimension()).thenReturn(1280);
    when(storageService.readFromBudgetBucket("previews/1280/candidates/q3.jpg.jpg"))
        .thenReturn(Optional.empty());
    when(thumbnailService.create(any())).thenReturn(thumbnail);

    BudgetAssetPayload result =
        service.fetchCandidateImage("Q3", "SKYSCRAPER", "sha3", true);

    assertThat(result).isEqualTo(thumbnail);
    verify(storageService)
        .uploadToBudgetBucket(
            eq("previews/1280/candidates/q3.jpg.jpg"),
            any(InputStream.class),
            eq(3L),
            eq("image/jpeg"));
  }

  @Test
  void servesPublishedProjectImageAsManagedThumbnail() {
    BudgetImageReference reference =
        new BudgetImageReference(
            "candidates/q4.jpg",
            "https://upload.wikimedia.org/thumb.jpg",
            "https://upload.wikimedia.org/original.jpg");
    BudgetAssetPayload thumbnail = new BudgetAssetPayload(new byte[] {10, 11}, "image/jpeg");
    when(repository.findPublishedProjectImageReference("Q4", "sha4"))
        .thenReturn(Optional.of(reference));
    when(thumbnailService.maximumDimension()).thenReturn(1280);
    when(storageService.readFromBudgetBucket("previews/1280/candidates/q4.jpg.jpg"))
        .thenReturn(Optional.empty());
    when(thumbnailService.create(any())).thenReturn(thumbnail);

    BudgetAssetPayload result = service.fetchPublishedProjectImage("Q4", "sha4");

    assertThat(result).isEqualTo(thumbnail);
  }

  @Test
  void cachesPublishedProjectResponsiveImageAndReusesItOnNextRequest() {
    BudgetImageReference reference =
        new BudgetImageReference("candidates/q7.jpg", null, null);
    BudgetAssetPayload thumbnail = new BudgetAssetPayload(new byte[] {14, 15}, "image/jpeg");
    StorageObjectPayload cached = new StorageObjectPayload(thumbnail.bytes(), thumbnail.contentType());
    when(repository.findPublishedProjectImageReference("Q7", "sha7"))
        .thenReturn(Optional.of(reference));
    when(storageService.readFromBudgetBucket("previews/480/candidates/q7.jpg.jpg"))
        .thenReturn(Optional.empty(), Optional.of(cached));
    when(thumbnailService.createWidthVariant(any(), eq(480))).thenReturn(thumbnail);

    assertThat(service.fetchPublishedProjectImage("Q7", "sha7", 480)).isEqualTo(thumbnail);
    assertThat(service.fetchPublishedProjectImage("Q7", "sha7", 480).bytes())
        .containsExactly(14, 15);

    verify(thumbnailService).createWidthVariant(any(), eq(480));
    verify(storageService).uploadToBudgetBucket(
        eq("previews/480/candidates/q7.jpg.jpg"), any(InputStream.class), eq(2L), eq("image/jpeg"));
  }

  @Test
  void rejectsUnboundedPublishedProjectImageWidths() {
    assertThatThrownBy(() -> service.fetchPublishedProjectImage("Q7", "sha7", 1234))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
  }

  @Test
  void createsAndCachesAllowedResponsiveVariant() {
    BudgetImageReference reference =
        new BudgetImageReference(
            "candidates/q5.webp",
            "https://upload.wikimedia.org/thumb.webp",
            "https://upload.wikimedia.org/original.webp");
    BudgetAssetPayload thumbnail = new BudgetAssetPayload(new byte[] {12, 13}, "image/jpeg");
    when(repository.findVariantImageReference(5L, BudgetImageRole.PRIMARY))
        .thenReturn(Optional.of(reference));
    when(storageService.readFromBudgetBucket("previews/480/candidates/q5.webp.jpg"))
        .thenReturn(Optional.empty());
    when(thumbnailService.createWidthVariant(any(), eq(480))).thenReturn(thumbnail);

    BudgetAssetPayload result = service.fetchVariantImage(5L, "primary", 480);

    assertThat(result).isEqualTo(thumbnail);
    verify(storageService)
        .uploadToBudgetBucket(
            eq("previews/480/candidates/q5.webp.jpg"),
            any(InputStream.class),
            eq(2L),
            eq("image/jpeg"));
  }

  @Test
  void rejectsArbitraryResponsiveDimensionsToPreventCacheAmplification() {
    BudgetImageReference reference =
        new BudgetImageReference(
            "candidates/q6.jpg",
            "https://upload.wikimedia.org/thumb.jpg",
            "https://upload.wikimedia.org/original.jpg");
    when(repository.findVariantImageReference(6L, BudgetImageRole.PRIMARY))
        .thenReturn(Optional.of(reference));

    assertThatThrownBy(() -> service.fetchVariantImage(6L, "primary", 1234))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
  }
}
