package io.clueterra.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.times;

import java.io.ByteArrayInputStream;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private StorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setEndpoint("http://minio:9000");
        properties.setPublicEndpoint("https://cdn.example.com");
        properties.setAccessKey("access");
        properties.setSecretKey("secret");
        properties.setBucket("avatars");
        properties.setMaxFileSize(1024);
        properties.setMaxHintImageSize(1024);
        service = new StorageService(minioClient, properties);
    }

    @Test
    @DisplayName("upload builds public avatar URL from publicEndpoint")
    void uploadUsesPublicEndpoint() throws Exception {
        String url;
        try (ByteArrayInputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3})) {
            url = service.upload(
                    42L,
                    stream,
                    3L,
                    "image/png",
                    "png");
        }

        assertThat(url)
                .startsWith("https://cdn.example.com/avatars/42/")
                .endsWith(".png");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("uploadToHintBucket builds public URL from publicEndpoint")
    void hintUploadUsesPublicEndpoint() throws Exception {
        String url;
        try (ByteArrayInputStream stream = new ByteArrayInputStream(new byte[]{1, 2})) {
            url = service.uploadToHintBucket(
                    "countries/de.png",
                    stream,
                    2L,
                    "image/png");
        }

        assertThat(url).isEqualTo("https://cdn.example.com/hint-images/countries/de.png");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("resolveHintAssetUrl builds proxy URL when hint image exists")
    void resolveHintAssetUrlUsesProxy() {
        String firstVersion = service.resolveHintAssetUrl(42L, "countries/de.png");
        String replacementVersion = service.resolveHintAssetUrl(42L, "countries/de-v2.png");

        assertThat(firstVersion)
                .startsWith("/api/assets/hints/42?v=");
        assertThat(replacementVersion).isNotEqualTo(firstVersion);
    }

    @Test
    @DisplayName("resolveHintAssetUrl hides bundled flag paths behind the hint proxy")
    void resolveHintAssetUrlHidesFlagPath() {
        assertThat(service.resolveHintAssetUrl(42L, "/api/assets/flags/cu.svg"))
                .startsWith("/api/assets/hints/42?v=")
                .doesNotContain("flags")
                .doesNotContain("cu");
    }

    @Test
    @DisplayName("hint asset versions are keyed by the storage secret")
    void hintAssetVersionDependsOnSecret() {
        StorageProperties otherProperties = new StorageProperties();
        otherProperties.setEndpoint("http://minio:9000");
        otherProperties.setPublicEndpoint("https://cdn.example.com");
        otherProperties.setSecretKey("other-secret");
        StorageService otherService = new StorageService(minioClient, otherProperties);

        String version = service.resolveHintAssetUrl(42L, "natural-earth/ad.svg");

        assertThat(version).matches("/api/assets/hints/42\\?v=[0-9a-f]{32}");
        assertThat(otherService.resolveHintAssetUrl(42L, "natural-earth/ad.svg")).isNotEqualTo(version);
    }

    @Test
    @DisplayName("resolveHintAssetUrl never falls back to a public bucket URL")
    void resolveHintAssetUrlRequiresHintId() {
        assertThat(service.resolveHintAssetUrl(null, "natural-earth/ad.svg")).isNull();
        assertThat(service.resolveHintAssetUrl(42L, " ")).isNull();
    }

    @Test
    @DisplayName("resolveStatisticAssetUrl builds a backend-owned statistic proxy URL")
    void resolveStatisticAssetUrlUsesProxy() {
        assertThat(service.resolveStatisticAssetUrl(84L, "wikimedia-currency/se/note.jpg"))
                .startsWith("/api/assets/statistics/84?v=");
    }

    @Test
    @DisplayName("extractHintObjectKey accepts public hint bucket URLs")
    void extractHintObjectKeyAcceptsPublicUrl() {
        assertThat(service.extractHintObjectKey("https://cdn.example.com/hint-images/countries/de.png"))
                .isEqualTo("countries/de.png");
    }

    @Test
    @DisplayName("extractHintObjectKey rejects local app asset paths")
    void extractHintObjectKeyRejectsLocalAssetPath() {
        assertThat(service.extractHintObjectKey("/api/assets/flags/de.svg")).isNull();
    }

    @Test
    @DisplayName("resolveBudgetCandidateAssetUrl includes the source image version")
    void resolveBudgetCandidateAssetUrlIsVersioned() {
        assertThat(service.resolveBudgetCandidateAssetUrl(
                "Q15",
                "BRIDGE",
                "abc123",
                "candidates/q15.jpg"))
                .isEqualTo("/api/assets/budget/candidates/Q15/BRIDGE/abc123");
    }

    @Test
    @DisplayName("resolveAvatarUrl rewrites internal MinIO URLs to public URLs")
    void resolveAvatarUrlNormalizesInternalUrl() {
        String resolved = service.resolveAvatarUrl("http://minio:9000/avatars/badges/1/icon.png");

        assertThat(resolved).isEqualTo("https://cdn.example.com/avatars/badges/1/icon.png");
    }

    @Test
    @DisplayName("resolveAvatarUrl drops external avatar URLs")
    void resolveAvatarUrlDropsExternalUrl() {
        assertThat(service.resolveAvatarUrl("https://avatars.githubusercontent.com/u/1")).isNull();
    }

    @Test
    @DisplayName("resolveUserAvatarUrl rewrites safe user avatar objects")
    void resolveUserAvatarUrlNormalizesSafeUserAvatar() {
        String resolved = service.resolveUserAvatarUrl("http://minio:9000/avatars/42/avatar.png");

        assertThat(resolved).isEqualTo("https://cdn.example.com/avatars/42/avatar.png");
    }

    @Test
    @DisplayName("resolveUserAvatarUrl drops legacy unsafe user avatar extensions")
    void resolveUserAvatarUrlDropsUnsafeUserAvatarExtension() {
        assertThat(service.resolveUserAvatarUrl("http://minio:9000/avatars/42/avatar.svg")).isNull();
        assertThat(service.resolveUserAvatarUrl("http://minio:9000/avatars/42/avatar.gif")).isNull();
    }

    @Test
    @DisplayName("isMinioUrl accepts both internal and public avatar URLs")
    void isMinioUrlAcceptsInternalAndPublicUrls() {
        assertThat(service.isMinioUrl("http://minio:9000/avatars/42/a.png")).isTrue();
        assertThat(service.isMinioUrl("https://cdn.example.com/avatars/42/a.png")).isTrue();
        assertThat(service.isMinioUrl("https://avatars.githubusercontent.com/u/1")).isFalse();
    }

    @Test
    @DisplayName("delete removes objects addressed by public avatar URLs")
    void deleteAcceptsPublicAvatarUrl() throws Exception {
        service.delete("https://cdn.example.com/avatars/42/public.png");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verifyNoMoreInteractions(minioClient);
    }

    @Test
    @DisplayName("deleting an uploaded avatar also removes its leaderboard thumbnail")
    void deleteRemovesDerivedLeaderboardThumbnail() throws Exception {
        String file = "0c1475b3-41cb-4eec-ae28-65f466530a23.png";
        service.delete("https://cdn.example.com/avatars/42/" + file);

        ArgumentCaptor<RemoveObjectArgs> removed = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, times(2)).removeObject(removed.capture());
        assertThat(removed.getAllValues())
                .extracting(RemoveObjectArgs::object)
                .containsExactly("42/" + file, "leaderboard/128/42/" + file + ".jpg");
    }
}
