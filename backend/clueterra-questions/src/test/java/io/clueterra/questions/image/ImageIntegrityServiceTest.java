package io.clueterra.questions.image;

import io.clueterra.questions.clients.hydrorivers.HydroRiverSkeletonVariant;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.OperationBatchLogRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.challenge.ChallengeRerollService;
import io.clueterra.questions.storage.ImageDownloadResult;
import io.clueterra.questions.storage.ImageStorageService;
import io.clueterra.questions.storage.MediaStorageAccessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageIntegrityServiceTest {

    @Test
    void abortsAuditWhenObjectInspectionLosesStorageConnectivity() {
        ImageAssetRepository repository = mock(ImageAssetRepository.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        ImageAuditCandidate candidate = candidate(
                1L,
                "ai/us/existing.jpg",
                "Alice",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/");
        when(repository.findAuditCandidates()).thenReturn(List.of(candidate));
        when(storage.extractObjectPath(candidate.imageUrl())).thenReturn(candidate.imageUrl());
        when(storage.objectExistsStrict("hint-images", candidate.imageUrl()))
                .thenThrow(new MediaStorageAccessException("MinIO unavailable", new RuntimeException("timeout")));
        ImageIntegrityService service = new ImageIntegrityService(
                repository,
                Optional.of(storage),
                List.of(),
                mock(WikimediaImageSourceAuditor.class),
                Optional.empty(),
                mock(ChallengeRerollService.class),
                mock(OperationBatchLogRepository.class),
                mock(ConsoleLogWriter.class),
                null);

        assertThatThrownBy(service::audit)
                .isInstanceOf(MediaStorageAccessException.class)
                .hasMessageContaining("MinIO unavailable");
    }

    @Test
    void failsAuditWhenAReferencedBucketCannotBeRead() {
        ImageAssetRepository repository = mock(ImageAssetRepository.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        ImageAuditCandidate candidate = candidate(1L, "ai/us/existing.jpg", "Alice", "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/");
        when(repository.findAuditCandidates()).thenReturn(List.of(candidate));
        doThrow(new IllegalStateException("Cannot access MinIO bucket hint-images"))
                .when(storage).verifyBucketAccess("hint-images");
        ImageIntegrityService service = new ImageIntegrityService(
                repository,
                Optional.of(storage),
                List.of(),
                mock(WikimediaImageSourceAuditor.class),
                Optional.empty(),
                mock(ChallengeRerollService.class),
                mock(OperationBatchLogRepository.class),
                mock(ConsoleLogWriter.class),
                null);

        assertThatThrownBy(service::audit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hint-images");

        verify(storage, never()).objectExistsStrict(any(), any());
    }

    @Test
    void restoresBundledRiverSkeletonUsingItsVariantAndPhysicalDataSourcePage() {
        ImageAssetRepository repository = mock(ImageAssetRepository.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        BundledImageAssetProvider riverAssetProvider = mock(BundledImageAssetProvider.class);
        ImageAuditCandidate candidate = new ImageAuditCandidate(
                ImageAssetOwnerType.HINT,
                7L,
                "River skeleton",
                "EG",
                "HYDRO_RIVERS",
                HydroRiverSkeletonVariant.MEDIUM.indicatorCode(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        ImageMetadata riverMetadata = new ImageMetadata(
                null,
                "https://www.hydrosheds.org/products/hydrorivers",
                "HydroRIVERS contributors",
                "HydroRIVERS attribution",
                "HydroSHEDS License Agreement",
                "https://www.hydrosheds.org/page/license");
        when(riverAssetProvider.source()).thenReturn(DataSource.HYDRO_RIVERS);
        when(riverAssetProvider.metadata()).thenReturn(riverMetadata);
        when(repository.findAuditCandidates()).thenReturn(List.of(candidate));
        when(storage.extractObjectPath(null)).thenReturn(null);
        when(riverAssetProvider.restoreObject(candidate))
                .thenReturn(Optional.of("hydrorivers/river-skeletons/eg-medium.svg"));
        ImageIntegrityService service = new ImageIntegrityService(
                repository,
                Optional.of(storage),
                List.of(riverAssetProvider),
                mock(WikimediaImageSourceAuditor.class),
                Optional.empty(),
                mock(ChallengeRerollService.class),
                mock(OperationBatchLogRepository.class),
                mock(ConsoleLogWriter.class),
                null);

        ImageIntegrityReport report = service.audit();

        assertThat(report.summary()).containsEntry("downloaded", 1);
        ArgumentCaptor<ImageMetadata> metadata = ArgumentCaptor.forClass(ImageMetadata.class);
        verify(repository).updateAfterRepair(
                eq(candidate),
                eq("hydrorivers/river-skeletons/eg-medium.svg"),
                metadata.capture());
        assertThat(metadata.getValue().sourcePageUrl()).contains("hydrorivers");
    }

    @Test
    void restoresMissingAiImageAndReportsSourceAndLicenseProblemsWithoutSanctions() {
        ImageAssetRepository repository = mock(ImageAssetRepository.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        WikimediaImageSourceAuditor wikimediaAuditor = mock(WikimediaImageSourceAuditor.class);
        ConsoleLogWriter console = mock(ConsoleLogWriter.class);
        ImageIntegrityService service = new ImageIntegrityService(
                repository,
                Optional.of(storage),
                List.of(),
                wikimediaAuditor,
                Optional.empty(),
                mock(ChallengeRerollService.class),
                mock(OperationBatchLogRepository.class),
                console,
                null);

        ImageAuditCandidate missingAi = candidate(1L, null, null, null, null);
        ImageAuditCandidate changedLicense = candidate(
                2L,
                "ai/us/existing.jpg",
                "Alice",
                "CC BY-SA 3.0",
                "https://creativecommons.org/licenses/by-sa/3.0/");
        ImageAuditCandidate deletedSource = candidate(
                3L,
                "ai/us/deleted.jpg",
                "Alice",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/");
        when(repository.findAuditCandidates()).thenReturn(List.of(missingAi, changedLicense, deletedSource));

        when(storage.extractObjectPath(null)).thenReturn(null);
        when(storage.extractObjectPath("ai/us/existing.jpg")).thenReturn("ai/us/existing.jpg");
        when(storage.extractObjectPath("ai/us/deleted.jpg")).thenReturn("ai/us/deleted.jpg");
        when(storage.objectExistsStrict("hint-images", "ai/us/existing.jpg")).thenReturn(true);
        when(storage.objectExistsStrict("hint-images", "ai/us/deleted.jpg")).thenReturn(true);
        when(storage.downloadAndStoreDetailed(
                eq("https://upload.wikimedia.org/wikipedia/commons/a/a1/Example-1.jpg"),
                eq("ai/us/hint-1")))
                .thenReturn(ImageDownloadResult.stored("ai/us/hint-1.jpg", 200));

        when(wikimediaAuditor.supports(any())).thenReturn(true);
        when(wikimediaAuditor.validate(missingAi)).thenReturn(ImageSourceValidation.available(metadata(
                "https://upload.wikimedia.org/wikipedia/commons/a/a1/Example-1.jpg"
        )));
        when(wikimediaAuditor.validate(changedLicense)).thenReturn(ImageSourceValidation.available(metadata(
                changedLicense.originalImageUrl()
        )));
        when(wikimediaAuditor.validate(deletedSource)).thenReturn(ImageSourceValidation.of(
                ImageSourceValidationStatus.SOURCE_MISSING,
                "Commons file page no longer exists"));

        ImageIntegrityReport report = service.audit();

        assertThat(report.totalItems()).isEqualTo(3);
        assertThat(report.successfulItems()).isEqualTo(2);
        assertThat(report.failedItems()).isEqualTo(1);
        assertThat(report.summary())
                .containsEntry("storageMissing", 1)
                .containsEntry("downloaded", 1)
                .containsEntry("licenseChanged", 1)
                .containsEntry("sourceMissing", 1)
                .containsEntry("metadataBackfilled", 1);
        assertThat((List<?>) report.summary().get("findings"))
                .anySatisfy(value -> {
                    Map<?, ?> finding = (Map<?, ?>) value;
                    assertThat(finding.get("ownerId")).isEqualTo("1");
                    assertThat(finding.get("restored")).isEqualTo(true);
                    assertThat(finding.get("metadataBackfilled")).isEqualTo(true);
                });
        verify(repository).updateAfterRepair(eq(missingAi), eq("ai/us/hint-1.jpg"), any(ImageMetadata.class));
        verify(repository).updateAfterRepair(
                eq(changedLicense), eq("ai/us/existing.jpg"), any(ImageMetadata.class));
        verify(repository, never()).updateAfterRepair(eq(deletedSource), any(), any());
    }

    @Test
    void ignoresWikimediaTrackingParametersWhenComparingSourceUrls() {
        ImageAssetRepository repository = mock(ImageAssetRepository.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        WikimediaImageSourceAuditor wikimediaAuditor = mock(WikimediaImageSourceAuditor.class);
        ImageIntegrityService service = new ImageIntegrityService(
                repository,
                Optional.of(storage),
                List.of(),
                wikimediaAuditor,
                Optional.empty(),
                mock(ChallengeRerollService.class),
                mock(OperationBatchLogRepository.class),
                mock(ConsoleLogWriter.class),
                null);
        String canonicalUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c9/Refuge_perafita_andorra.jpg";
        ImageAuditCandidate candidate = new ImageAuditCandidate(
                ImageAssetOwnerType.HINT,
                4L,
                "Andorra valley",
                "AD",
                "AI_GENERATED",
                "AI_GENERATED_4",
                "ai/ad/existing.jpg",
                canonicalUrl,
                "https://commons.wikimedia.org/wiki/File:Refuge_perafita_andorra.jpg",
                "Example Author",
                "Image by Example Author via Wikimedia Commons.",
                "CC BY 2.0",
                "https://creativecommons.org/licenses/by/2.0",
                canonicalUrl);
        when(repository.findAuditCandidates()).thenReturn(List.of(candidate));
        when(storage.extractObjectPath(candidate.imageUrl())).thenReturn(candidate.imageUrl());
        when(storage.objectExistsStrict("hint-images", candidate.imageUrl())).thenReturn(true);
        when(wikimediaAuditor.supports(candidate)).thenReturn(true);
        when(wikimediaAuditor.validate(candidate)).thenReturn(ImageSourceValidation.available(new ImageMetadata(
                canonicalUrl + "?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original",
                candidate.imageSourcePageUrl(),
                candidate.imageAuthorName(),
                candidate.imageAttributionText(),
                candidate.imageLicenseLabel(),
                candidate.imageLicenseUrl())));

        ImageIntegrityReport report = service.audit();

        assertThat(report.failedItems()).isZero();
        assertThat(report.summary())
                .containsEntry("sourceUrlChanged", 0)
                .containsEntry("metadataBackfilled", 0);
        assertThat((List<?>) report.summary().get("anomalies")).isEmpty();
        assertThat((List<?>) report.summary().get("findings")).isEmpty();
        verify(repository, never()).updateAfterRepair(any(), any(), any());
    }

    @Test
    void doesNotSilentlyReplaceMissingStoredBytesWhenTheSourceRevisionChanged() {
        ImageAssetRepository repository = mock(ImageAssetRepository.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        WikimediaImageSourceAuditor wikimediaAuditor = mock(WikimediaImageSourceAuditor.class);
        ChallengeRerollService rerollService = mock(ChallengeRerollService.class);
        ImageAuditCandidate candidate = new ImageAuditCandidate(
                ImageAssetOwnerType.HINT,
                44L,
                "Changed Commons image",
                "US",
                "AI_GENERATED",
                "AI_GENERATED_44",
                "ai/us/changed.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/a/a1/Changed.jpg",
                "https://commons.wikimedia.org/wiki/File:Changed.jpg",
                "Alice",
                "Alice — Own work",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/",
                "https://upload.wikimedia.org/wikipedia/commons/a/a1/Changed.jpg",
                404L,
                "WIKIMEDIA_COMMONS",
                "https://commons.wikimedia.org/wiki/File:Changed.jpg",
                "hint-images",
                "ai/us/changed.jpg",
                "old-sha1",
                "ACTIVE",
                0,
                1);
        when(repository.findAuditCandidates()).thenReturn(List.of(candidate));
        when(storage.objectExistsStrict("hint-images", candidate.objectPath())).thenReturn(false);
        when(wikimediaAuditor.supports(candidate)).thenReturn(true);
        when(wikimediaAuditor.validate(candidate)).thenReturn(ImageSourceValidation.available(
                new ImageMetadata(
                        candidate.originalImageUrl(),
                        candidate.imageSourcePageUrl(),
                        candidate.imageAuthorName(),
                        candidate.imageAttributionText(),
                        candidate.imageLicenseLabel(),
                        candidate.imageLicenseUrl(),
                        "https://thumb.wikimedia.org/changed.jpg",
                        "new-sha1",
                        "image/jpeg",
                        2048,
                        1365)));
        ImageIntegrityService service = new ImageIntegrityService(
                repository,
                Optional.of(storage),
                List.of(),
                wikimediaAuditor,
                Optional.empty(),
                rerollService,
                mock(OperationBatchLogRepository.class),
                mock(ConsoleLogWriter.class),
                null);

        ImageIntegrityReport report = service.audit();

        assertThat(report.failedItems()).isOne();
        verify(storage, never()).downloadAndStoreDetailed(any(), any());
        verify(repository, never()).updateAfterRepair(any(), any(), any());
        verify(repository).findChallengeReferences(404L, true);
        verify(repository).quarantine(
                eq(candidate),
                eq("Stored image is missing and the external source content changed; automatic replacement was blocked"),
                eq(true));
    }

    @Test
    void quarantinesResourceAfterThreeConsecutiveStorageRepairFailures() {
        ImageAssetRepository repository = mock(ImageAssetRepository.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        WikimediaImageSourceAuditor wikimediaAuditor = mock(WikimediaImageSourceAuditor.class);
        ChallengeRerollService rerollService = mock(ChallengeRerollService.class);
        ImageAuditCandidate candidate = new ImageAuditCandidate(
                ImageAssetOwnerType.HINT,
                45L,
                "Oversized Commons image",
                "US",
                "AI_GENERATED",
                "AI_GENERATED_45",
                "ai/us/oversized.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/a/a1/Oversized.jpg",
                "https://commons.wikimedia.org/wiki/File:Oversized.jpg",
                "Alice",
                "Alice - Own work",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/",
                "https://upload.wikimedia.org/wikipedia/commons/a/a1/Oversized.jpg",
                405L,
                "WIKIMEDIA_COMMONS",
                "file:oversized.jpg",
                "hint-images",
                "ai/us/oversized.jpg",
                "same-sha1",
                "ACTIVE",
                2,
                1);
        ImageMetadata metadata = new ImageMetadata(
                candidate.originalImageUrl(),
                candidate.imageSourcePageUrl(),
                candidate.imageAuthorName(),
                candidate.imageAttributionText(),
                candidate.imageLicenseLabel(),
                candidate.imageLicenseUrl(),
                candidate.originalImageUrl(),
                candidate.sourceSha1(),
                "image/jpeg",
                8000,
                6000);
        when(repository.findAuditCandidates()).thenReturn(List.of(candidate));
        when(storage.objectExistsStrict("hint-images", candidate.objectPath())).thenReturn(false);
        when(wikimediaAuditor.supports(candidate)).thenReturn(true);
        when(wikimediaAuditor.validate(candidate)).thenReturn(ImageSourceValidation.available(metadata));
        when(storage.downloadAndStoreDetailed(candidate.originalImageUrl(), candidate.objectPath()))
                .thenReturn(ImageDownloadResult.failed(null, "Image exceeds the configured download limit"));
        when(repository.recordValidation(eq(candidate), any(ImageSourceValidation.class))).thenReturn(3);
        ImageIntegrityService service = new ImageIntegrityService(
                repository,
                Optional.of(storage),
                List.of(),
                wikimediaAuditor,
                Optional.empty(),
                rerollService,
                mock(OperationBatchLogRepository.class),
                mock(ConsoleLogWriter.class),
                null);

        service.audit();

        ArgumentCaptor<ImageSourceValidation> validation =
                ArgumentCaptor.forClass(ImageSourceValidation.class);
        verify(repository).recordValidation(eq(candidate), validation.capture());
        assertThat(validation.getValue().status()).isEqualTo(ImageSourceValidationStatus.FETCH_FAILED);
        verify(repository).findChallengeReferences(405L, false);
        verify(repository).quarantine(
                candidate,
                "Image exceeds the configured download limit",
                false);
    }

    private ImageAuditCandidate candidate(
            long id,
            String imageUrl,
            String author,
            String license,
            String licenseUrl) {
        String originalUrl = "https://upload.wikimedia.org/wikipedia/commons/a/a1/Example-" + id + ".jpg";
        return new ImageAuditCandidate(
                ImageAssetOwnerType.HINT,
                id,
                "AI hint " + id,
                "US",
                "AI_GENERATED",
                "AI_GENERATED_" + id,
                imageUrl,
                id == 1L ? null : originalUrl,
                id == 1L ? null : "https://commons.wikimedia.org/wiki/File:Example-" + id + ".jpg",
                author,
                author != null ? author + " — Own work" : null,
                license,
                licenseUrl,
                originalUrl);
    }

    private ImageMetadata metadata(String originalUrl) {
        return new ImageMetadata(
                originalUrl,
                "https://commons.wikimedia.org/wiki/File:" + originalUrl.substring(originalUrl.lastIndexOf('/') + 1),
                "Alice",
                "Alice" + " — Own work",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/");
    }
}
