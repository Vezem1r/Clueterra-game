package io.clueterra.api.admin.users.service;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.admin.users.dto.AdminUserDto;
import io.clueterra.api.admin.users.dto.UpdateUserProfileRequest;
import io.clueterra.api.admin.users.dto.UpdateUserRequest;
import io.clueterra.api.admin.users.dto.UserPageResponse;
import io.clueterra.api.admin.users.repository.AdminUserJdbcRepository;
import io.clueterra.api.auth.service.IdentityNormalizer;
import io.clueterra.api.auth.service.RefreshTokenService;

import io.clueterra.api.common.enums.Role;
import io.clueterra.api.exception.ResourceConflictException;
import io.clueterra.api.profile.service.AvatarImageService;
import io.clueterra.api.profile.service.ProcessedAvatar;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service("adminUserService")
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final AdminUserJdbcRepository userRepo;
    private final StorageService storageService;
    private final SettingsService settingsService;
    private final AvatarImageService avatarImageService;
    private final RefreshTokenService refreshTokenService;

    public UserPageResponse listUsers(int page, int size, String search, String role,
                                      Boolean enabled, Boolean reportsMuted, Boolean isGuest,
                                      Integer minRiversPlayed, Integer maxRiversPlayed,
                                      String sortBy, String sortDir) {
        boolean filteringByRole = role != null && !role.isBlank();
        boolean filteringByAdminRole = filteringByRole && Role.ADMIN.name().equalsIgnoreCase(role);

        List<AdminUserDto> pinned;
        if (filteringByAdminRole) {
            pinned = userRepo.findAdmins(search, enabled, minRiversPlayed, maxRiversPlayed).stream()
                    .map(this::resolveAvatarUrl)
                    .toList();
            return UserPageResponse.of(pinned, Page.empty());
        }

        pinned = filteringByRole ? List.of() : userRepo.findAdmins(search, enabled, minRiversPlayed, maxRiversPlayed).stream()
                .map(this::resolveAvatarUrl)
                .toList();
        boolean excludeAdmins = !filteringByRole;
        Page<AdminUserDto> content = userRepo.findAll(search, role, enabled, reportsMuted, isGuest,
                minRiversPlayed, maxRiversPlayed, sortBy, sortDir, PageRequest.of(page, size), excludeAdmins);
        Page<AdminUserDto> resolvedContent = content.map(this::resolveAvatarUrl);
        return UserPageResponse.of(pinned, resolvedContent);
    }

    public Optional<AdminUserDto> getUser(Long id) {
        return userRepo.findById(id).map(this::resolveAvatarUrl);
    }

    public Optional<AdminUserDto> updateUser(Long id, UpdateUserRequest request) {
        Optional<AdminUserDto> existing = userRepo.findById(id);
        boolean isExistingAdmin = existing.isPresent()
                && Role.ADMIN.name().equalsIgnoreCase(existing.get().role());

        boolean isAdmin = Role.ADMIN.name().equalsIgnoreCase(request.role());
        if (request.role() != null && isAdmin && !isExistingAdmin) {
            throw new ResourceConflictException("Cannot assign ADMIN role");
        }
        if (isExistingAdmin && request.role() != null && !isAdmin) {
            throw new ResourceConflictException("Cannot change role of an admin user");
        }
        int rows = userRepo.update(id, request.role(), request.enabled(), request.reportsMuted(), request.profileLocked());
        if (rows == 0) return Optional.empty();
        if (Boolean.FALSE.equals(request.enabled())) {
            refreshTokenService.revokeAll(id);
        }
        log.info("User updated by admin: userId={}, role={}, enabled={}, reportsMuted={}", id, request.role(), request.enabled(), request.reportsMuted());
        return userRepo.findById(id).map(this::resolveAvatarUrl);
    }

    public Optional<AdminUserDto> updateProfile(Long userId, UpdateUserProfileRequest request) {
        if (request.username() != null && !request.username().isBlank()) {
            String username = IdentityNormalizer.normalizeUsername(request.username());
            if (userRepo.existsByUsername(userId, username)) {
                throw new ResourceConflictException("Username is already taken");
            }
            try {
                userRepo.updateUsername(userId, username);
            } catch (DuplicateKeyException exception) {
                throw new ResourceConflictException("Username is already taken");
            }
        }
        userRepo.updateProfile(userId, request.displayName(), request.totalXp(),
                request.challengesPlayed(), request.challengesWon(),
                request.currentStreak(), request.bestStreak(), request.streakFreezes(),
                settingsService.getLevelDivisor());
        log.info("User profile updated by admin: userId={}", userId);
        return userRepo.findById(userId).map(this::resolveAvatarUrl);
    }

    public Optional<AdminUserDto> uploadAvatar(Long userId, MultipartFile file) {
        Optional<AdminUserDto> existing = userRepo.findById(userId);
        if (existing.isEmpty()) return Optional.empty();

        AdminUserDto user = existing.get();
        String previousAvatarUrl = user.avatarUrl();

        ProcessedAvatar avatar = avatarImageService.process(file);
        String url = storageService.upload(
                userId,
                new ByteArrayInputStream(avatar.data()),
                avatar.data().length,
                avatar.contentType(),
                avatar.extension());

        try {
            userRepo.updateAvatarUrl(userId, url);
        } catch (RuntimeException e) {
            if (storageService.isMinioUrl(url)) {
                storageService.delete(url);
            }
            throw e;
        }

        if (previousAvatarUrl != null
                && storageService.isMinioUrl(previousAvatarUrl)
                && !previousAvatarUrl.equals(url)) {
            storageService.delete(previousAvatarUrl);
        }

        log.info("User avatar updated by admin: userId={}", userId);
        return userRepo.findById(userId).map(this::resolveAvatarUrl);
    }

    public void deleteAvatar(Long userId) {
        userRepo.findById(userId).ifPresent(user -> {
            if (user.avatarUrl() != null && storageService.isMinioUrl(user.avatarUrl())) {
                storageService.delete(user.avatarUrl());
            }
            userRepo.updateAvatarUrl(userId, null);
            log.info("User avatar deleted by admin: userId={}", userId);
        });
    }

    public List<String> getRoles() {
        return userRepo.findDistinctRoles();
    }

    private AdminUserDto resolveAvatarUrl(AdminUserDto dto) {
        return new AdminUserDto(
                dto.id(),
                dto.email(),
                dto.username(),
                dto.role(),
                dto.enabled(),
                dto.createdAt(),
                dto.displayName(),
                storageService.resolveUserAvatarUrl(dto.avatarUrl()),
                dto.totalXp(),
                dto.currentLevel(),
                dto.challengesPlayed(),
                dto.challengesWon(),
                dto.currentStreak(),
                dto.bestStreak(),
                dto.streakFreezes(),
                dto.budgetCompletedChallenges(),
                dto.budgetAnsweredQuestions(),
                dto.budgetFullXpRate(),
                dto.budgetAverageAccuracy(),
                dto.budgetCleanSweeps(),
                dto.budgetProjectsExplored(),
                dto.budgetBestHigherLowerStreak(),
                dto.budgetProjectsMasteredValueUsd(),
                dto.riverStats(),
                dto.silhouetteStats(),
                dto.flagStats(),
                dto.quizStats(),
                dto.reportsMuted(),
                dto.profileLocked(),
                dto.isGuest());
    }
}
