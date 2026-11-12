package io.clueterra.api.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.assets.repository.BadgeAssetJdbcRepository;
import io.clueterra.api.storage.StorageObjectPayload;
import io.clueterra.api.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class BadgeAssetServiceTest {

    @Mock
    private BadgeAssetJdbcRepository repository;
    @Mock
    private StorageService storageService;
    @Mock
    private BadgeImageProcessor imageProcessor;

    @InjectMocks
    private BadgeAssetService service;

    @Test
    void lazilyOptimizesAndCachesExistingBadgeImage() {
        String storedUrl = "https://cdn.example.com/avatars/badges/7/original.png";
        when(storageService.extractAvatarObjectKey(storedUrl)).thenReturn("badges/7/original.png");
        String assetUrl = service.resolveUrl(7L, storedUrl);
        String version = assetUrl.substring(assetUrl.lastIndexOf('/') + 1, assetUrl.length() - 4);
        String cacheKey = "badges/optimized/384/7/" + version + ".png";

        when(repository.findImageUrl(7L)).thenReturn(Optional.of(storedUrl));
        when(imageProcessor.maximumDimension()).thenReturn(384);
        when(storageService.readFromAvatarBucket(cacheKey)).thenReturn(Optional.empty());
        when(storageService.readFromAvatarBucket("badges/7/original.png"))
                .thenReturn(Optional.of(new StorageObjectPayload(new byte[]{1, 2, 3}, "image/png")));
        when(imageProcessor.process(new byte[]{1, 2, 3}))
                .thenReturn(new BadgeAssetPayload(new byte[]{9, 8}, "image/png"));

        BadgeAssetPayload result = service.fetch(7L, version);

        assertThat(result.bytes()).containsExactly(9, 8);
        verify(storageService).uploadToAvatarBucket(
                eq(cacheKey), any(), eq(2L), eq("image/png"));
    }

    @Test
    void rejectsStaleVersionBeforeReadingImageBytes() {
        String storedUrl = "https://cdn.example.com/avatars/badges/7/original.png";
        when(repository.findImageUrl(7L)).thenReturn(Optional.of(storedUrl));

        assertThatThrownBy(() -> service.fetch(7L, "0000000000000000"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");

        verifyNoMoreInteractions(storageService, imageProcessor);
    }
}
