package io.clueterra.api.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.assets.repository.QuestionImageAssetJdbcRepository;
import io.clueterra.api.assets.repository.QuestionImageReference;
import io.clueterra.api.storage.StorageObjectPayload;
import io.clueterra.api.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class QuestionImageAssetServiceTest {

  @Mock private QuestionImageAssetJdbcRepository imageAssetRepository;
  @Mock private StorageService storageService;
  @Mock private MapillaryImageUrlResolver mapillaryImageUrlResolver;
  @Mock private BudgetImageThumbnailService thumbnailService;
  @Mock private FlagAssetService flagAssetService;

  private QuestionImageAssetService service;

  @BeforeEach
  void setUp() {
    service = new QuestionImageAssetService(
        imageAssetRepository, storageService, mapillaryImageUrlResolver, thumbnailService,
        flagAssetService);
  }

  @Test
  @DisplayName("fetchHint serves a bundled flag hint without touching storage")
  void fetchHintServesBundledFlag() {
    QuestionImageReference reference =
        new QuestionImageReference("/api/assets/flags/cu.svg", "/api/assets/flags/cu.svg", null, "MANUAL");

    when(imageAssetRepository.findHintImageReference(42L)).thenReturn(Optional.of(reference));
    when(flagAssetService.fetchFlag("cu"))
        .thenReturn(new FlagAssetPayload(new byte[] {1, 2}, "image/svg+xml"));

    QuestionImageAssetPayload payload = service.fetchHint(42L, 960);

    assertThat(payload.bytes()).containsExactly(1, 2);
    assertThat(payload.contentType()).isEqualTo("image/svg+xml");
    verify(thumbnailService, never()).createWidthVariant(any(), anyInt());
    verify(storageService, never()).readFromHintBucket(any());
  }

  @Test
  @DisplayName("fetchHint serves vector hints as-is instead of rasterizing a preview")
  void fetchHintServesVectorWithoutPreview() {
    QuestionImageReference reference =
        new QuestionImageReference("natural-earth/ad.svg", null, null, "NATURAL_EARTH");
    StorageObjectPayload stored = new StorageObjectPayload(new byte[] {3}, "image/svg+xml");

    when(imageAssetRepository.findHintImageReference(42L)).thenReturn(Optional.of(reference));
    when(storageService.extractHintObjectKey("natural-earth/ad.svg")).thenReturn("natural-earth/ad.svg");
    when(storageService.readFromHintBucket("natural-earth/ad.svg")).thenReturn(Optional.of(stored));

    QuestionImageAssetPayload payload = service.fetchHint(42L, 960);

    assertThat(payload.bytes()).containsExactly(3);
    verify(thumbnailService, never()).createWidthVariant(any(), anyInt());
  }

  @Test
  @DisplayName("fetchHint rejects unsupported preview widths")
  void fetchHintRejectsUnsupportedWidth() {
    QuestionImageReference reference =
        new QuestionImageReference("natural-earth/ad.svg", null, null, "NATURAL_EARTH");
    when(imageAssetRepository.findHintImageReference(42L)).thenReturn(Optional.of(reference));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.fetchHint(42L, 123))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode().value()).isEqualTo(400));
  }

  @Test
  @DisplayName("fetchHint serves cached MinIO image without remote download")
  void fetchHintUsesCachedObject() {
    String storedUrl = "https://cdn.example.com/hint-images/mapillary/de/pano.jpg";
    QuestionImageReference reference =
        new QuestionImageReference(storedUrl, "https://images.example.com/pano.jpg", null, "MAPILLARY");
    StorageObjectPayload cached = new StorageObjectPayload(new byte[] {1, 2, 3}, "image/jpeg");

    when(imageAssetRepository.findHintImageReference(42L)).thenReturn(Optional.of(reference));
    when(storageService.extractHintObjectKey(storedUrl)).thenReturn("mapillary/de/pano.jpg");
    when(storageService.readFromHintBucket("mapillary/de/pano.jpg"))
        .thenReturn(Optional.of(cached));

    QuestionImageAssetPayload payload = service.fetchHint(42L);

    assertThat(payload.bytes()).containsExactly(1, 2, 3);
    assertThat(payload.contentType()).isEqualTo("image/jpeg");
    verify(storageService, never()).downloadRemoteImage(any());
    verify(storageService, never())
        .uploadToHintBucket(any(), any(InputStream.class), any(Long.class), any());
  }

  @Test
  @DisplayName("fetchHint creates and caches a responsive preview while preserving the original")
  void fetchHintCreatesResponsivePreview() {
    String storedUrl = "https://cdn.example.com/hint-images/wikimedia/de/landmark.jpg";
    String objectPath = "wikimedia/de/landmark.jpg";
    String previewPath = "previews/960/wikimedia/de/landmark.jpg.jpg";
    QuestionImageReference reference =
        new QuestionImageReference(storedUrl, null, null, "WIKIMEDIA_LANDMARKS");
    BudgetAssetPayload preview = new BudgetAssetPayload(new byte[] {7, 8}, "image/jpeg");

    when(imageAssetRepository.findHintImageReference(42L)).thenReturn(Optional.of(reference));
    when(storageService.extractHintObjectKey(storedUrl)).thenReturn(objectPath);
    when(storageService.readFromHintBucket(previewPath)).thenReturn(Optional.empty());
    when(thumbnailService.createWidthVariant(any(), eq(960))).thenReturn(preview);

    QuestionImageAssetPayload payload = service.fetchHint(42L, 960);

    assertThat(payload.bytes()).containsExactly(7, 8);
    assertThat(payload.contentType()).isEqualTo("image/jpeg");
    verify(storageService)
        .uploadToHintBucket(
            eq(previewPath), any(InputStream.class), eq(2L), eq("image/jpeg"));
  }

  @Test
  @DisplayName("fetchHint serves a cached responsive preview without decoding the original")
  void fetchHintUsesCachedResponsivePreview() {
    String storedUrl = "https://cdn.example.com/hint-images/wikimedia/de/landmark.jpg";
    String objectPath = "wikimedia/de/landmark.jpg";
    String previewPath = "previews/960/wikimedia/de/landmark.jpg.jpg";
    QuestionImageReference reference =
        new QuestionImageReference(storedUrl, null, null, "WIKIMEDIA_LANDMARKS");
    StorageObjectPayload preview = new StorageObjectPayload(new byte[] {7, 8}, "image/jpeg");

    when(imageAssetRepository.findHintImageReference(42L)).thenReturn(Optional.of(reference));
    when(storageService.extractHintObjectKey(storedUrl)).thenReturn(objectPath);
    when(storageService.readFromHintBucket(previewPath)).thenReturn(Optional.of(preview));

    QuestionImageAssetPayload payload = service.fetchHint(42L, 960);

    assertThat(payload.bytes()).containsExactly(7, 8);
    verify(thumbnailService, never()).createWidthVariant(any(), anyInt());
    verify(storageService, never())
        .uploadToHintBucket(any(), any(InputStream.class), any(Long.class), any());
  }

  @Test
  @DisplayName("fetchStatistic serves the statistic image through the same managed storage path")
  void fetchStatisticUsesCachedObject() {
    String storedUrl = "https://cdn.example.com/hint-images/wikimedia-currency/se/note.jpg";
    QuestionImageReference reference =
        new QuestionImageReference(
            storedUrl,
            "https://upload.wikimedia.org/note.jpg",
            "https://commons.wikimedia.org/wiki/File:Note.jpg",
            "WIKIMEDIA_CURRENCY");
    StorageObjectPayload cached = new StorageObjectPayload(new byte[] {5, 6, 7}, "image/jpeg");

    when(imageAssetRepository.findStatisticImageReference(84L)).thenReturn(Optional.of(reference));
    when(storageService.extractHintObjectKey(storedUrl))
        .thenReturn("wikimedia-currency/se/note.jpg");
    when(storageService.readFromHintBucket("wikimedia-currency/se/note.jpg"))
        .thenReturn(Optional.of(cached));

    QuestionImageAssetPayload payload = service.fetchStatistic(84L);

    assertThat(payload.bytes()).containsExactly(5, 6, 7);
    assertThat(payload.contentType()).isEqualTo("image/jpeg");
    verify(storageService, never()).downloadRemoteImage(any());
  }

  @Test
  @DisplayName("fetchHint restores missing MinIO image from original source into same object key")
  void fetchHintRestoresMissingObject() {
    String storedUrl = "https://cdn.example.com/hint-images/mapillary/de/pano.jpg";
    String originalUrl = "https://images.example.com/pano.jpg";
    QuestionImageReference reference =
        new QuestionImageReference(storedUrl, originalUrl, null, "MAPILLARY");
    StorageObjectPayload restored = new StorageObjectPayload(new byte[] {7, 8, 9}, "image/jpeg");

    when(imageAssetRepository.findHintImageReference(42L)).thenReturn(Optional.of(reference));
    when(storageService.extractHintObjectKey(storedUrl)).thenReturn("mapillary/de/pano.jpg");
    when(storageService.readFromHintBucket("mapillary/de/pano.jpg")).thenReturn(Optional.empty());
    when(storageService.extractHintObjectKey(originalUrl)).thenReturn(null);
    when(storageService.downloadRemoteImage(originalUrl)).thenReturn(Optional.of(restored));

    QuestionImageAssetPayload payload = service.fetchHint(42L);

    assertThat(payload.bytes()).containsExactly(7, 8, 9);
    assertThat(payload.contentType()).isEqualTo("image/jpeg");
    verify(storageService)
        .uploadToHintBucket(
            eq("mapillary/de/pano.jpg"), any(InputStream.class), eq(3L), eq("image/jpeg"));
  }

  @Test
  @DisplayName("fetchHint proxies external image when no MinIO key exists")
  void fetchHintUsesExternalImageWhenNoStoredKeyExists() {
    String remoteUrl = "https://images.example.com/flag.png";
    QuestionImageReference reference =
        new QuestionImageReference(remoteUrl, null, null, "WIKIMEDIA_LANDMARKS");
    StorageObjectPayload restored = new StorageObjectPayload(new byte[] {4, 5}, "image/png");

    when(imageAssetRepository.findHintImageReference(99L)).thenReturn(Optional.of(reference));
    when(storageService.extractHintObjectKey(remoteUrl)).thenReturn(null);
    when(storageService.downloadRemoteImage(remoteUrl)).thenReturn(Optional.of(restored));

    QuestionImageAssetPayload payload = service.fetchHint(99L);

    assertThat(payload.bytes()).containsExactly(4, 5);
    assertThat(payload.contentType()).isEqualTo("image/png");
    verify(storageService, never())
        .uploadToHintBucket(any(), any(InputStream.class), any(Long.class), any());
  }

  @Test
  @DisplayName("fetchHint reports a missing asset instead of an upstream service failure")
  void fetchHintReturnsNotFoundWhenRemoteSourceDisappearedBeforeCaching() {
    String remoteUrl = "https://images.example.com/deleted.png";
    QuestionImageReference reference =
        new QuestionImageReference(remoteUrl, null, null, "MANUAL");

    when(imageAssetRepository.findHintImageReference(99L)).thenReturn(Optional.of(reference));
    when(storageService.extractHintObjectKey(remoteUrl)).thenReturn(null);
    when(storageService.downloadRemoteImage(remoteUrl)).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.fetchHint(99L))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode().value()).isEqualTo(404));
  }

  @Test
  @DisplayName("fetchHint restores missing Mapillary image through a fresh source URL")
  void fetchHintRestoresMapillaryObjectFromFreshSourceUrl() {
    String storedUrl = "mapillary/0q6f4vcb5w9fx.jpg";
    String expiredOriginalUrl = "https://scontent-ams2-1.xx.fbcdn.net/expired.jpg";
    String sourcePageUrl = "https://www.mapillary.com/app/?pKey=602611771775426&focus=photo";
    String freshUrl = "https://scontent-ams2-1.xx.fbcdn.net/fresh.jpg";
    QuestionImageReference reference =
        new QuestionImageReference(storedUrl, expiredOriginalUrl, sourcePageUrl, "MAPILLARY");
    StorageObjectPayload restored = new StorageObjectPayload(new byte[] {10, 11}, "image/jpeg");

    when(imageAssetRepository.findHintImageReference(77L)).thenReturn(Optional.of(reference));
    when(storageService.extractHintObjectKey(storedUrl)).thenReturn("mapillary/0q6f4vcb5w9fx.jpg");
    when(storageService.readFromHintBucket("mapillary/0q6f4vcb5w9fx.jpg"))
        .thenReturn(Optional.empty());
    when(mapillaryImageUrlResolver.resolveFreshImageUrl(sourcePageUrl))
        .thenReturn(Optional.of(freshUrl));
    when(storageService.extractHintObjectKey(expiredOriginalUrl)).thenReturn(null);
    when(storageService.downloadRemoteImage(freshUrl)).thenReturn(Optional.of(restored));

    QuestionImageAssetPayload payload = service.fetchHint(77L);

    assertThat(payload.bytes()).containsExactly(10, 11);
    assertThat(payload.contentType()).isEqualTo("image/jpeg");
    verify(storageService).downloadRemoteImage(freshUrl);
    verify(storageService, never()).downloadRemoteImage(expiredOriginalUrl);
    verify(storageService)
        .uploadToHintBucket(
            eq("mapillary/0q6f4vcb5w9fx.jpg"), any(InputStream.class), eq(2L), eq("image/jpeg"));
  }
}
