package io.clueterra.api.admin.badges.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.badges.dto.BadgeAdminDto;
import io.clueterra.api.admin.badges.dto.BadgeEvalLogDto;
import io.clueterra.api.admin.badges.dto.BadgeTranslationDto;
import io.clueterra.api.admin.badges.dto.SaveBadgeRequest;
import io.clueterra.api.admin.badges.dto.UserBadgeAdminDto;
import io.clueterra.api.admin.badges.repository.AdminBadgeJdbcRepository;
import io.clueterra.api.achievements.engine.BadgeEvaluationService;
import io.clueterra.api.common.enums.BadgeColorPreset;
import io.clueterra.api.common.enums.EvaluationType;
import io.clueterra.api.exception.ResourceNotFoundException;
import io.clueterra.api.storage.StorageService;
import io.hypersistence.tsid.TSID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBadgeService {

    private final AdminBadgeJdbcRepository badgeRepository;
    private final BadgeEvaluationService evaluationService;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    public Page<BadgeAdminDto> findAll(String search, String grantType, Boolean isSecret,
                                       Boolean isActive, String sortBy, String sortDir,
                                       int page, int size) {
        return badgeRepository.findAll(search, grantType, isSecret, isActive, sortBy, sortDir, page, size)
                .map(this::resolveBadgeImageUrl);
    }

    public Optional<BadgeAdminDto> findById(Long id) {
        return badgeRepository.findById(id).map(this::resolveBadgeImageUrl);
    }

    @Transactional
    public BadgeAdminDto create(SaveBadgeRequest request) {
        validateCreate(request);

        Long id = TSID.Factory.getTsid().toLong();
        String grantConfig = serializeGrantConfig(request.grantConfig());

        badgeRepository.insert(
                id,
                request.slug(),
                resolveCreateColorKey(request.accentColorKey(), BadgeColorPreset.DEFAULT_ACCENT_KEY, "accentColorKey"),
                resolveCreateColorKey(request.effectColorKey(), BadgeColorPreset.DEFAULT_EFFECT_KEY, "effectColorKey"),
                Boolean.TRUE.equals(request.isSecret()),
                request.isActive() == null || request.isActive(),
                request.grantType(),
                grantConfig,
                request.displayOrder() != null ? request.displayOrder() : 0,
                request.categoryId()
        );

        if (request.translations() != null) {
            badgeRepository.replaceTranslations(id, request.translations());
        }

        log.info("Badge template created: id={}, slug={}, grantType={}", id, request.slug(), request.grantType());
        return badgeRepository.findById(id)
                .map(this::resolveBadgeImageUrl)
                .orElseThrow(() -> new ResourceNotFoundException("Badge not found with id: " + id));
    }

    @Transactional
    public Optional<BadgeAdminDto> update(Long id, SaveBadgeRequest request) {
        if (request.slug() != null && badgeRepository.existsBySlugExcluding(request.slug(), id)) {
            throw new IllegalArgumentException("Slug already in use");
        }

        String grantConfig = serializeGrantConfig(request.grantConfig());

        int rows = badgeRepository.update(
                id,
                request.slug(),
                resolveUpdateColorKey(request.accentColorKey(), "accentColorKey"),
                resolveUpdateColorKey(request.effectColorKey(), "effectColorKey"),
                request.isSecret(),
                request.isActive(),
                request.grantType(),
                grantConfig,
                request.displayOrder(),
                request.categoryId());

        if (rows == 0) return Optional.empty();

        if (request.translations() != null && !request.translations().isEmpty()) {
            badgeRepository.replaceTranslations(id, request.translations());
        }

        log.info("Badge template updated: id={}, slug={}", id, request.slug());
        return badgeRepository.findById(id).map(this::resolveBadgeImageUrl);
    }

    public boolean delete(Long id) {
        boolean deleted = badgeRepository.delete(id) > 0;
        if (deleted) {
            log.info("Badge template deleted: id={}", id);
        }
        return deleted;
    }

    public List<UserBadgeAdminDto> getUserBadges(Long userId) {
        return badgeRepository.findUserBadges(userId).stream()
                .map(this::resolveBadgeImageUrl)
                .toList();
    }

    public void grantBadge(Long userId, Long badgeId, String grantedBy) {
        badgeRepository.grantBadge(userId, badgeId, grantedBy);
        log.info("Badge manually granted: userId={}, badgeId={}, adminId={}", userId, badgeId, grantedBy);
    }

    public void revokeBadge(Long userId, Long badgeId) {
        badgeRepository.revokeBadge(userId, badgeId);
        log.info("Badge revoked: userId={}, badgeId={}", userId, badgeId);
    }

    public Page<BadgeEvalLogDto> getEvalLog(Long badgeId, int page, int size) {
        return badgeRepository.findEvalLog(badgeId, page, size);
    }

    public void evaluateAllCustomBadges() {
        evaluationService.evaluateAllCustomBadges(EvaluationType.MANUAL.name());
    }

    public void evaluateSingleBadge(Long badgeId) {
        evaluationService.evaluateSingleBadge(badgeId, EvaluationType.MANUAL.name());
    }

    public void reorderBadges(List<Long> orderedIds) {
        if (orderedIds.isEmpty()) throw new IllegalArgumentException("orderedIds must not be empty");
        badgeRepository.bulkUpdateBadgeOrder(orderedIds);
    }

    private void validateCreate(SaveBadgeRequest request) {
        if (request.slug() == null || request.slug().isBlank()) {
            throw new IllegalArgumentException("Slug is required");
        }
        if (request.grantType() == null || request.grantType().isBlank()) {
            throw new IllegalArgumentException("Grant type is required");
        }
        if (request.translations() == null || !request.translations().containsKey("en")) {
            throw new IllegalArgumentException("English translation is required");
        }
        BadgeTranslationDto en = request.translations().get("en");
        if (en.name() == null || en.name().isBlank()) {
            throw new IllegalArgumentException("English name is required");
        }
        if (en.descriptionUnlocked() == null || en.descriptionUnlocked().isBlank()) {
            throw new IllegalArgumentException("English unlocked description is required");
        }
        if (en.descriptionLocked() == null || en.descriptionLocked().isBlank()) {
            throw new IllegalArgumentException("English locked description is required");
        }
        if (badgeRepository.existsBySlug(request.slug())) {
            throw new IllegalArgumentException("Slug already in use");
        }
    }

    private String serializeGrantConfig(Object grantConfig) {
        if (grantConfig == null) return null;
        try {
            return objectMapper.writeValueAsString(grantConfig);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid grant config JSON", e);
        }
    }

    private String resolveCreateColorKey(String key, String defaultKey, String fieldName) {
        if (key == null || key.isBlank()) {
            return defaultKey;
        }
        return validateColorKey(key, fieldName);
    }

    private String resolveUpdateColorKey(String key, String fieldName) {
        if (key == null) {
            return null;
        }
        return validateColorKey(key, fieldName);
    }

    private String validateColorKey(String key, String fieldName) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (!BadgeColorPreset.isValidKey(normalized)) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return normalized;
    }

    private BadgeAdminDto resolveBadgeImageUrl(BadgeAdminDto dto) {
        return new BadgeAdminDto(
                dto.id(),
                dto.slug(),
                storageService.resolveAvatarUrl(dto.imageUrl()),
                dto.accentColorKey(),
                dto.effectColorKey(),
                dto.isSecret(),
                dto.isActive(),
                dto.grantType(),
                dto.grantConfig(),
                dto.displayOrder(),
                dto.ownerCount(),
                dto.createdAt(),
                dto.updatedAt(),
                dto.translations(),
                dto.categoryId(),
                dto.categoryName());
    }

    private UserBadgeAdminDto resolveBadgeImageUrl(UserBadgeAdminDto dto) {
        return new UserBadgeAdminDto(
                dto.badgeId(),
                dto.slug(),
                dto.name(),
                storageService.resolveAvatarUrl(dto.imageUrl()),
                dto.grantedAt(),
                dto.grantedBy());
    }
}
