package io.clueterra.api.profile.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.clueterra.api.profile.dto.AcknowledgeBadgesRequest;
import io.clueterra.api.profile.dto.ProfileBadgeDto;
import io.clueterra.api.profile.dto.ProfileBadgePageResponse;
import io.clueterra.api.profile.dto.ProfileDto;
import io.clueterra.api.profile.dto.UnacknowledgedBadgeDto;
import io.clueterra.api.profile.dto.UpdateProfileRequest;
import io.clueterra.api.profile.service.ProfileService;
import io.clueterra.api.security.RegisteredUserOnly;
import io.clueterra.api.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@Tag(name = "Profile", description = "User profile management")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/profile")
    @RegisteredUserOnly
    public ResponseEntity<ProfileDto> getOwnProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.getOwnProfile(principal.getId()));
    }

    @GetMapping("/profile/guest")
    @PreAuthorize("isAuthenticated() and principal.guest")
    @Operation(summary = "Get the current guest's private profile")
    public ResponseEntity<ProfileDto> getGuestProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.getOwnProfile(principal.getId()));
    }

    @GetMapping("/profile/{username}")
    public ResponseEntity<ProfileDto> getPublicProfile(@PathVariable String username) {
        return ResponseEntity.ok(profileService.getPublicProfile(username));
    }

    @PutMapping("/profile")
    @RegisteredUserOnly
    public ResponseEntity<ProfileDto> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(principal.getId(), request));
    }

    @PostMapping("/profile/avatar")
    @RegisteredUserOnly
    public ResponseEntity<ProfileDto> uploadAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(profileService.uploadAvatar(principal.getId(), file));
    }

    @DeleteMapping("/profile/avatar")
    @RegisteredUserOnly
    public ResponseEntity<ProfileDto> deleteAvatar(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.deleteAvatar(principal.getId()));
    }

    @GetMapping("/profile/badges")
    @RegisteredUserOnly
    public ResponseEntity<ProfileBadgePageResponse> getBadgesPaginated(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "9") @Min(1) @Max(50) int size,
            @RequestHeader(value = "Accept-Language", defaultValue = "en") String lang) {
        String locale = extractLocale(lang);
        return ResponseEntity.ok(profileService.getBadgesPaginated(principal.getId(), locale, filter, page, size));
    }

    @GetMapping("/profile/guest/badges")
    @PreAuthorize("isAuthenticated() and principal.guest")
    @Operation(summary = "Get private badges for the current guest")
    public ResponseEntity<ProfileBadgePageResponse> getGuestBadgesPaginated(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "earned") String filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "9") @Min(1) @Max(50) int size,
            @RequestHeader(value = "Accept-Language", defaultValue = "en") String lang) {
        return ResponseEntity.ok(profileService.getBadgesPaginated(
                principal.getId(), extractLocale(lang), filter, page, size));
    }

    @GetMapping("/profile/{username}/badges")
    public ResponseEntity<ProfileBadgePageResponse> getPublicBadgesPaginated(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "9") @Min(1) @Max(50) int size,
            @RequestHeader(value = "Accept-Language", defaultValue = "en") String lang) {
        String locale = extractLocale(lang);
        return ResponseEntity.ok(profileService.getPublicBadgesPaginated(username, locale, page, size));
    }

    @GetMapping("/badges/samples")
    public ResponseEntity<List<ProfileBadgeDto>> getRandomBadgeSamples(
            @RequestHeader(value = "Accept-Language", defaultValue = "en") String lang) {
        String locale = extractLocale(lang);
        return ResponseEntity.ok(profileService.getRandomBadgeSamples(locale));
    }

    @GetMapping("/profile/badges/unacknowledged")
    @RegisteredUserOnly
    public ResponseEntity<List<UnacknowledgedBadgeDto>> getUnacknowledged(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Accept-Language", defaultValue = "en") String lang) {
        String locale = extractLocale(lang);
        return ResponseEntity.ok(profileService.getUnacknowledged(principal.getId(), locale));
    }

    @PostMapping("/profile/badges/acknowledge")
    @RegisteredUserOnly
    public ResponseEntity<Void> acknowledgeBadges(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AcknowledgeBadgesRequest request) {
        profileService.acknowledgeBadges(principal.getId(), request.badgeIds());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/profile/streak-freeze-notice/acknowledge")
    @RegisteredUserOnly
    @Operation(summary = "Acknowledge streak freeze notice")
    public ResponseEntity<Void> acknowledgeStreakFreezeNotice(
            @AuthenticationPrincipal UserPrincipal principal) {
        profileService.acknowledgeStreakFreezeNotice(principal.getId());
        return ResponseEntity.ok().build();
    }

    private String extractLocale(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return "en";
        String primary = acceptLanguage.split(",")[0].split(";")[0].trim().toLowerCase();
        if (primary.startsWith("ru")) return "ru";
        if (primary.startsWith("cs")) return "cs";
        return "en";
    }
}
