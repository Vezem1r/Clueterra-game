package io.clueterra.questions.rivergame.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.clueterra.questions.image.ImageMetadata;
import io.clueterra.questions.image.ImageSourceValidation;
import io.clueterra.questions.image.ImageSourceValidationStatus;
import io.clueterra.questions.image.WikimediaImageSourceAuditor;
import io.clueterra.questions.storage.ImageDownloadResult;
import io.clueterra.questions.storage.ImageStorageService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RiverPhotoResolverTest {

    private final WikimediaImageSourceAuditor auditor = mock(WikimediaImageSourceAuditor.class);
    private final RiverMediaRepository repository = mock(RiverMediaRepository.class);
    private final ImageStorageService storage = mock(ImageStorageService.class);
    private final RiverPhotoResolver resolver = new RiverPhotoResolver(auditor, repository);

    @Test
    void skipsMapsAndStoresTheFirstLicensedLandscapePhotoUnderAnOpaqueKey() {
        when(auditor.validate(argThat(candidate -> candidate != null && candidate.imageSourcePageUrl().endsWith("Rhine_valley.jpg"))))
                .thenReturn(ImageSourceValidation.available(metadata("image/jpeg", 4000, 2600)));
        when(repository.findPhotoLifecycleStatus(anyString(), anyString())).thenReturn(Optional.empty());
        when(storage.downloadAndStoreDetailed(anyString(), anyString())).thenAnswer(invocation ->
                ImageDownloadResult.stored(invocation.getArgument(1), 200, 90_000, "f".repeat(64), "image/jpeg"));

        RiverPhotoResolution resolution = resolver.resolve(record("Rhine river map.jpg", "Rhine valley.jpg"), storage);

        assertThat(resolution.status()).isEqualTo(RiverPhotoResolutionStatus.FOUND);
        RiverPhoto photo = resolution.photo().orElseThrow();
        assertThat(photo.objectKey()).matches("river-photos/[0-9a-f]{64}\\.jpg").doesNotContain("Rhine");
        assertThat(photo.licenseLabel()).isEqualTo("CC BY-SA 4.0");
        assertThat(photo.byteSize()).isEqualTo(90_000L);
        verify(auditor, never()).validate(argThat(candidate -> candidate != null && candidate.imageSourcePageUrl().contains("map")));
    }

    @Test
    void rejectsQuarantinedOrUnsuitablePhotosWithoutDownloading() {
        when(auditor.validate(argThat(candidate -> candidate != null && candidate.imageSourcePageUrl().endsWith("Panorama.jpg"))))
                .thenReturn(ImageSourceValidation.available(metadata("image/jpeg", 9000, 1000)));
        when(auditor.validate(argThat(candidate -> candidate != null && candidate.imageSourcePageUrl().endsWith("Bridge.jpg"))))
                .thenReturn(ImageSourceValidation.available(metadata("image/jpeg", 3000, 2000)));
        when(repository.findPhotoLifecycleStatus(anyString(), anyString())).thenReturn(Optional.of("QUARANTINED"));

        RiverPhotoResolution resolution = resolver.resolve(record("Panorama.jpg", "Bridge.jpg", "Logo.svg"), storage);

        assertThat(resolution.status()).isEqualTo(RiverPhotoResolutionStatus.NONE);
        assertThat(resolution.reason()).contains("unsuitable size", "quarantined", "not a photo file");
        verify(storage, never()).downloadAndStoreDetailed(anyString(), anyString());
    }

    @Test
    void sourceOutageKeepsTheCurrentPhotoInsteadOfClearingIt() {
        when(auditor.validate(any())).thenReturn(
                ImageSourceValidation.of(ImageSourceValidationStatus.FETCH_FAILED, "HTTP 503"));

        RiverPhotoResolution resolution = resolver.resolve(record("Rhine valley.jpg"), storage);

        assertThat(resolution.status()).isEqualTo(RiverPhotoResolutionStatus.FAILED);
        assertThat(resolution.reason()).contains("HTTP 503");
    }

    @Test
    void riverWithoutWikidataImageHasNoPhoto() {
        RiverPhotoResolution resolution = resolver.resolve(record(), storage);

        assertThat(resolution.status()).isEqualTo(RiverPhotoResolutionStatus.NONE);
    }

    private RiverWikidataRecord record(String... files) {
        return new RiverWikidataRecord("q584", "https://en.wikipedia.org/wiki/Rhine", List.of(files));
    }

    private ImageMetadata metadata(String mimeType, int width, int height) {
        return new ImageMetadata("https://upload.wikimedia.org/wikipedia/commons/9/99/Rhine.jpg",
                "https://commons.wikimedia.org/wiki/File:Rhine.jpg", "Jane Doe", "Jane Doe",
                "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0",
                "https://upload.wikimedia.org/thumb/Rhine.jpg", "1".repeat(40), mimeType, width, height);
    }
}
