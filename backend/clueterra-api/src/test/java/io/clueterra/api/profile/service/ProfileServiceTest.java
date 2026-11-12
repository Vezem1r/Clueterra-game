package io.clueterra.api.profile.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.assets.service.BadgeAssetService;
import io.clueterra.api.profile.dto.ProfileDifficultyStatsDto;
import io.clueterra.api.profile.dto.ProfileDto;
import io.clueterra.api.profile.dto.UnacknowledgedBadgeDto;
import io.clueterra.api.profile.dto.UpdateProfileRequest;
import io.clueterra.api.profile.repository.ProfileJdbcRepository;
import io.clueterra.api.profile.repository.ProfileRow;
import io.clueterra.api.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final long USER_ID = 42L;
    private static final Instant LATE_EVENING_UTC = Instant.parse("2026-11-12T23:30:00Z");

    @Mock
    private ProfileJdbcRepository profileRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private AvatarImageService avatarImageService;
    @Mock
    private ProfileBadgeProgressService badgeProgressService;
    @Mock
    private BadgeAssetService badgeAssetService;

    @Mock
    private Clock clock;

    @InjectMocks
    private ProfileService service;

    @BeforeEach
    void stopTheClock() {
        lenient().when(clock.instant()).thenReturn(LATE_EVENING_UTC);
    }

    @Test
    @DisplayName("uploadAvatar deletes previous MinIO avatar and stores replacement URL")
    void uploadAvatarReplacesPreviousMinioObject() {
        String oldUrl = "http://minio:9000/avatars/42/old.png";
        String newUrl = "https://cdn.example.com/avatars/42/new.png";
        ProfileRow current = profileRow(oldUrl);
        ProfileRow updated = profileRow(newUrl);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        ProcessedAvatar avatar = new ProcessedAvatar(new byte[]{9, 8, 7}, "image/png", "png");

        when(profileRepository.findOwnProfile(USER_ID))
                .thenReturn(Optional.of(current))
                .thenReturn(Optional.of(updated));
        when(avatarImageService.process(file)).thenReturn(avatar);
        when(storageService.isMinioUrl(oldUrl)).thenReturn(true);
        when(storageService.upload(eq(USER_ID), any(), eq(3L), eq("image/png"), eq("png")))
                .thenReturn(newUrl);
        when(storageService.resolveUserAvatarUrl(newUrl)).thenReturn(newUrl);
        when(settingsService.getLevelDivisor()).thenReturn(100);

        ProfileDto result = service.uploadAvatar(USER_ID, file);

        inOrder(profileRepository, storageService).verify(profileRepository).updateAvatarUrl(USER_ID, newUrl);
        verify(storageService).delete(oldUrl);
        verify(profileRepository).updateAvatarUrl(USER_ID, newUrl);
        assertThat(result.avatarUrl()).isEqualTo(newUrl);
    }

    @Test
    @DisplayName("uploadAvatar removes newly uploaded object if profile update fails")
    void uploadAvatarDeletesNewObjectWhenProfileUpdateFails() {
        String oldUrl = "http://minio:9000/avatars/42/old.png";
        String newUrl = "https://cdn.example.com/avatars/42/new.png";
        ProfileRow current = profileRow(oldUrl);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        ProcessedAvatar avatar = new ProcessedAvatar(new byte[]{9, 8, 7}, "image/png", "png");

        when(profileRepository.findOwnProfile(USER_ID)).thenReturn(Optional.of(current));
        when(avatarImageService.process(file)).thenReturn(avatar);
        when(storageService.upload(eq(USER_ID), any(), eq(3L), eq("image/png"), eq("png")))
                .thenReturn(newUrl);
        when(storageService.isMinioUrl(newUrl)).thenReturn(true);
        doThrow(new IllegalStateException("db failed")).when(profileRepository).updateAvatarUrl(USER_ID, newUrl);

        assertThatThrownBy(() -> service.uploadAvatar(USER_ID, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db failed");

        verify(storageService).delete(newUrl);
        verify(storageService, never()).delete(oldUrl);
    }

    @Test
    @DisplayName("deleteAvatar removes the stored MinIO object before clearing avatarUrl")
    void deleteAvatarRemovesStoredObject() {
        String oldUrl = "http://minio:9000/avatars/42/old.png";
        ProfileRow current = profileRow(oldUrl);
        ProfileRow updated = profileRow(null);

        when(profileRepository.findOwnProfile(USER_ID))
                .thenReturn(Optional.of(current))
                .thenReturn(Optional.of(updated));
        when(storageService.isMinioUrl(oldUrl)).thenReturn(true);
        when(settingsService.getLevelDivisor()).thenReturn(100);

        ProfileDto result = service.deleteAvatar(USER_ID);

        verify(storageService).delete(oldUrl);
        verify(profileRepository).updateAvatarUrl(USER_ID, null);
        assertThat(result.avatarUrl()).isNull();
    }

    @Test
    @DisplayName("getUnacknowledged resolves internal badge image URLs to public URLs")
    void getUnacknowledgedResolvesBadgeImageUrls() {
        String storedUrl = "http://minio:9000/avatars/badges/7/icon.png";
        String publicUrl = "https://cdn.example.com/avatars/badges/7/icon.png";

        when(profileRepository.findUnacknowledged(USER_ID, "en")).thenReturn(List.of(
                new UnacknowledgedBadgeDto(
                        100L,
                        7L,
                        "first-badge",
                        storedUrl,
                        "sky-core",
                        "amber-glow",
                        "First Badge",
                        "Unlocked",
                        "2026-04-12T10:00:00Z")
        ));
        when(badgeAssetService.resolveUrl(7L, storedUrl)).thenReturn(publicUrl);

        List<UnacknowledgedBadgeDto> result = service.getUnacknowledged(USER_ID, "en");

        assertThat(result).singleElement().satisfies(badge ->
                assertThat(badge.imageUrl()).isEqualTo(publicUrl));
    }

    @Test
    @DisplayName("getOwnProfile resolves avatar without hydrating badges into profile payload")
    void getOwnProfileDoesNotHydrateBadges() {
        String storedUrl = "http://minio:9000/avatars/42/avatar.png";
        String publicUrl = "https://cdn.example.com/avatars/42/avatar.png";
        ProfileRow current = profileRow(storedUrl);

        when(profileRepository.findOwnProfile(USER_ID)).thenReturn(Optional.of(current));
        when(profileRepository.findDifficultyStats(USER_ID)).thenReturn(List.of(
                new ProfileDifficultyStatsDto(
                        "EASY", 18, 72, 71, 98.6, Instant.parse("2026-06-20T10:00:00Z")),
                new ProfileDifficultyStatsDto(
                        "MEDIUM", 1, 5, 5, 100.0, Instant.parse("2026-06-19T10:00:00Z"))
        ));
        when(storageService.resolveUserAvatarUrl(storedUrl)).thenReturn(publicUrl);
        when(settingsService.getLevelDivisor()).thenReturn(100);

        ProfileDto result = service.getOwnProfile(USER_ID);

        assertThat(result.avatarUrl()).isEqualTo(publicUrl);
        assertThat(result.difficultyRecommendation()).isEqualTo("MEDIUM");
        verify(profileRepository, never()).countHiddenSecrets(anyLong());
    }

    @Test
    @DisplayName("getOwnProfile exposes pending streak freeze notice")
    void getOwnProfileExposesPendingStreakFreezeNotice() {
        ProfileRow current = profileRow(null, 3, 1);

        when(profileRepository.findOwnProfile(USER_ID)).thenReturn(Optional.of(current));
        when(profileRepository.findDifficultyStats(USER_ID)).thenReturn(List.of());
        when(settingsService.getLevelDivisor()).thenReturn(100);

        ProfileDto result = service.getOwnProfile(USER_ID);

        assertThat(result.freezesUsedSinceLastCompletion()).isEqualTo(3);
        assertThat(result.freezeNoticeAcknowledged()).isFalse();
    }

    @Test
    @DisplayName("getOwnProfile counts the streak down to the end of the UTC day, not to the reset job")
    void getOwnProfileCountsDownToUtcMidnight() {
        when(profileRepository.findOwnProfile(USER_ID)).thenReturn(Optional.of(profileRow(null, 0, 0)));
        when(profileRepository.findDifficultyStats(USER_ID)).thenReturn(List.of());
        when(settingsService.getLevelDivisor()).thenReturn(100);

        assertThat(service.getOwnProfile(USER_ID).resetAtUtc()).isEqualTo("2026-11-13T00:00:00Z");
    }

    @Test
    @DisplayName("acknowledgeStreakFreezeNotice delegates to profile repository")
    void acknowledgeStreakFreezeNoticeDelegates() {
        service.acknowledgeStreakFreezeNotice(USER_ID);

        verify(profileRepository).acknowledgeStreakFreezeNotice(USER_ID);
    }

    @Test
    @DisplayName("getOwnProfile suppresses difficulty advice when the target difficulty was already played last")
    void getOwnProfileSuppressesStaleDifficultyRecommendation() {
        String storedUrl = "http://minio:9000/avatars/42/avatar.png";
        String publicUrl = "https://cdn.example.com/avatars/42/avatar.png";
        ProfileRow current = profileRow(storedUrl);

        when(profileRepository.findOwnProfile(USER_ID)).thenReturn(Optional.of(current));
        when(profileRepository.findDifficultyStats(USER_ID)).thenReturn(List.of(
                new ProfileDifficultyStatsDto(
                        "EASY", 18, 72, 71, 98.6, Instant.parse("2026-06-19T10:00:00Z")),
                new ProfileDifficultyStatsDto(
                        "MEDIUM", 1, 5, 5, 100.0, Instant.parse("2026-06-20T10:00:00Z"))
        ));
        when(storageService.resolveUserAvatarUrl(storedUrl)).thenReturn(publicUrl);
        when(settingsService.getLevelDivisor()).thenReturn(100);

        ProfileDto result = service.getOwnProfile(USER_ID);

        assertThat(result.avatarUrl()).isEqualTo(publicUrl);
        assertThat(result.difficultyRecommendation()).isNull();
        verify(profileRepository, never()).countHiddenSecrets(anyLong());
    }

    @Test
    @DisplayName("updateProfile rejects external avatar URLs")
    void updateProfileRejectsExternalAvatarUrls() {
        when(profileRepository.findOwnProfile(USER_ID)).thenReturn(Optional.of(profileRow(null)));
        when(storageService.isMinioUrl("https://tracker.example.com/avatar.png")).thenReturn(false);

        assertThatThrownBy(() -> service.updateProfile(
                USER_ID,
                new UpdateProfileRequest("User", "https://tracker.example.com/avatar.png", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("External avatar URLs are not allowed");

        verify(profileRepository, never()).upsertProfile(anyLong(), any(), any(), any());
    }

    private static ProfileRow profileRow(String avatarUrl) {
        return profileRow(avatarUrl, 0, 0);
    }

    private static ProfileRow profileRow(
            String avatarUrl,
            int freezesUsedSinceCompletion,
            int freezesAcknowledgedSinceCompletion) {
        return new ProfileRow(
                "user",
                "user@example.com",
                "User",
                avatarUrl,
                100L,
                2,
                10,
                5,
                3,
                50.0,
                7,
                9,
                4,
                "history",
                false,
                0,
                freezesUsedSinceCompletion,
                freezesAcknowledgedSinceCompletion,
                false,
                false,
                true,
                0,
                0,
                BigDecimal.ZERO);
    }
}
