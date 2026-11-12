package io.clueterra.api.admin.hints.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.api.admin.hints.dto.HintAdminDto;
import io.clueterra.api.admin.hints.dto.HintImageUpdate;
import io.clueterra.api.admin.hints.dto.ReplaceHintImageRequest;
import io.clueterra.api.admin.hints.dto.UpdateHintRequest;
import io.clueterra.api.admin.hints.image.DownloadedWikimediaImage;
import io.clueterra.api.admin.hints.image.WikimediaCommonsImageClient;
import io.clueterra.api.admin.hints.image.WikimediaHintImage;
import io.clueterra.api.admin.hints.repository.AdminHintJdbcRepository;
import io.clueterra.api.admin.hints.repository.HintCountryJdbcRepository;
import io.clueterra.api.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class HintServiceTest {

    @Mock
    private AdminHintJdbcRepository hintRepository;
    @Mock
    private HintCountryJdbcRepository countryRepository;
    @Mock
    private HintCacheService cacheService;
    @Mock
    private StorageService storageService;
    @Mock
    private WikimediaCommonsImageClient wikimediaImageClient;

    private HintService service;

    @BeforeEach
    void setUp() {
        service = new HintService(
                hintRepository,
                countryRepository,
                cacheService,
                storageService,
                wikimediaImageClient);
    }

    @Test
    void replacesImageFactWithValidatedWikimediaImageAndAttribution() {
        HintAdminDto existing = hint("IMAGE_FACT", null);
        WikimediaHintImage wikimediaImage = wikimediaImage();
        DownloadedWikimediaImage downloaded =
                new DownloadedWikimediaImage(new byte[]{1, 2, 3}, "image/jpeg", "jpg");
        HintAdminDto updated = hint("IMAGE_FACT", "/api/assets/hints/42");

        when(hintRepository.findById(42L)).thenReturn(Optional.of(existing));
        when(wikimediaImageClient.findExactImage("File:Victoria Falls.jpg"))
                .thenReturn(wikimediaImage);
        when(wikimediaImageClient.download(wikimediaImage)).thenReturn(downloaded);
        when(storageService.uploadToHintBucket(
                eq("wikimedia-admin/zw/42/abc123def4567890.jpg"),
                any(InputStream.class),
                eq(3L),
                eq("image/jpeg")))
                .thenReturn("https://cdn.example/hint-images/wikimedia-admin/zw/42/abc123def4567890.jpg");
        when(hintRepository.updateImage(eq(42L), any(HintImageUpdate.class)))
                .thenReturn(Optional.of(updated));

        Optional<HintAdminDto> result = service.replaceHintImage(
                42L, new ReplaceHintImageRequest("File:Victoria Falls.jpg"));

        assertThat(result).contains(updated);
        ArgumentCaptor<HintImageUpdate> updateCaptor = ArgumentCaptor.forClass(HintImageUpdate.class);
        verify(hintRepository).updateImage(eq(42L), updateCaptor.capture());
        HintImageUpdate update = updateCaptor.getValue();
        assertThat(update.imageUrl()).isEqualTo(
                "https://cdn.example/hint-images/wikimedia-admin/zw/42/abc123def4567890.jpg");
        assertThat(update.originalImageUrl()).isEqualTo(wikimediaImage.originalUrl());
        assertThat(update.imageSourcePageUrl()).isEqualTo(wikimediaImage.sourcePageUrl());
        assertThat(update.imageAuthorName()).isEqualTo("Diego Delso");
        assertThat(update.imageLicenseLabel()).isEqualTo("CC BY-SA 4.0");
        assertThat(update.contentRevisionHash()).hasSize(64);
        verify(cacheService).evictOnMutation();
    }

    @Test
    void rejectsImagesForNonImageFactHints() {
        when(hintRepository.findById(42L)).thenReturn(Optional.of(hint("CULTURAL", null)));

        assertThatThrownBy(() -> service.replaceHintImage(
                42L, new ReplaceHintImageRequest("File:Victoria Falls.jpg")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IMAGE_FACT");

        verify(wikimediaImageClient, never()).findExactImage(any());
        verify(storageService, never()).uploadToHintBucket(any(), any(), eq(0L), any());
    }

    @Test
    void returnsEmptyWhenHintDoesNotExist() {
        when(hintRepository.findById(42L)).thenReturn(Optional.empty());

        Optional<HintAdminDto> result = service.replaceHintImage(
                42L, new ReplaceHintImageRequest("Victoria Falls.jpg"));

        assertThat(result).isEmpty();
        verify(wikimediaImageClient, never()).findExactImage(any());
    }

    @Test
    void editingStatusUsesTheSameDurableSuppressionAsTheDedicatedToggle() {
        UpdateHintRequest request = new UpdateHintRequest(null, null, null, false);
        HintAdminDto updated = hint("IMAGE_FACT", null);
        when(hintRepository.update(42L, null, null, null, false))
                .thenReturn(Optional.of(updated));

        Optional<HintAdminDto> result = service.updateHint(42L, request);

        assertThat(result).contains(updated);
        verify(hintRepository).createIdentitySuppression(
                42L,
                "QUARANTINE",
                "Hint deactivated by an administrator");
        verify(cacheService).evictOnMutation();
    }

    @Test
    void activationResolvesTheIdentitySuppression() {
        HintAdminDto updated = hint("IMAGE_FACT", null);
        when(hintRepository.setActive(42L, true)).thenReturn(Optional.of(updated));

        Optional<HintAdminDto> result = service.toggleActive(42L, true);

        assertThat(result).contains(updated);
        verify(hintRepository).resolveIdentitySuppression(42L);
        verify(cacheService).evictOnMutation();
    }

    @Test
    void deletingOnlyTheImageSuppressesThatCanonicalImage() {
        HintAdminDto updated = hint("IMAGE_FACT", null);
        when(hintRepository.clearImageFields(42L)).thenReturn(Optional.of(updated));

        Optional<HintAdminDto> result = service.deleteHintImage(42L);

        assertThat(result).contains(updated);
        verify(hintRepository).createImageSuppression(
                42L,
                "Hint image detached by an administrator");
        verify(cacheService).evictOnMutation();
    }

    @Test
    void deletingHintCreatesPermanentIdentitySuppression() {
        when(hintRepository.softDelete(42L)).thenReturn(true);

        boolean result = service.softDelete(42L);

        assertThat(result).isTrue();
        verify(hintRepository).createIdentitySuppression(
                42L,
                "DELETE",
                "Hint deleted by an administrator");
        verify(cacheService).evictOnMutation();
    }

    private static HintAdminDto hint(String hintType, String imageUrl) {
        return new HintAdminDto(
                42L,
                7L,
                "ZW",
                "Zimbabwe",
                "GEOGRAPHY",
                hintType,
                "This waterfall lies on a major southern African river.",
                imageUrl,
                null,
                null,
                null,
                null,
                null,
                null,
                55,
                new BigDecimal("0.90"),
                "WIKIMEDIA_LANDMARKS",
                "VICTORIA_FALLS",
                null,
                1,
                0,
                0,
                true,
                LocalDateTime.parse("2026-09-01T12:00:00"),
                LocalDateTime.parse("2026-09-01T12:00:00"));
    }

    private static WikimediaHintImage wikimediaImage() {
        return new WikimediaHintImage(
                "File:Victoria Falls.jpg",
                "abc123def4567890",
                "https://thumb.wikimedia.org/victoria-falls.jpg",
                "https://upload.wikimedia.org/victoria-falls.jpg",
                "https://commons.wikimedia.org/wiki/File:Victoria_Falls.jpg",
                "Diego Delso",
                "Image by Diego Delso via Wikimedia Commons.",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0",
                "image/jpeg",
                1_000_000,
                1600,
                900);
    }
}
