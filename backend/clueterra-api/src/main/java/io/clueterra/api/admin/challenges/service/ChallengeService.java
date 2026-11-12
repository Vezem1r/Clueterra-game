package io.clueterra.api.admin.challenges.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.clueterra.api.admin.challenges.dto.ChallengeAdminDto;
import io.clueterra.api.admin.challenges.dto.ChallengePageResponse;
import io.clueterra.api.admin.challenges.repository.AdminChallengeJdbcRepository;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChallengeService {

    private static final TypeReference<List<Map<String, Object>>> HINTS_JSON_REF = new TypeReference<>() {
    };

    private final AdminChallengeJdbcRepository challengeRepo;
    private final ChallengeCacheService cache;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;
    private final Clock clock;

    public ChallengePageResponse listChallenges(int page, int size, LocalDate startDate, LocalDate endDate, String level, String difficulty, Boolean active, String sortBy, String sortDir) {
        LocalDate today = LocalDate.now(clock);
        boolean todayInRange = (startDate == null || !today.isBefore(startDate)) && (endDate == null || !today.isAfter(endDate));
        List<ChallengeAdminDto> pinned = todayInRange ? challengeRepo.findByDate(today, level, difficulty, active) : List.of();

        Pageable pageable = PageRequest.of(page, size);
        LocalDate excludeDate = pinned.isEmpty() ? null : today;

        String key = cache.listKey(page, size, startDate, endDate, level, difficulty, active, sortBy, sortDir) + (excludeDate != null ? ":ex:" + excludeDate : "");

        Page<ChallengeAdminDto> contentPage;
        Optional<Page<ChallengeAdminDto>> cached = cache.getList(key, pageable);
        if (cached.isPresent()) {
            contentPage = cached.get();
        } else {
            contentPage = challengeRepo.findAll(startDate, endDate, level, difficulty, active, sortBy, sortDir, pageable, excludeDate);
            cache.putList(key, contentPage);
        }

        List<ChallengeAdminDto> pinnedWithState = pinned.stream()
                .map(challenge -> withAdminState(challenge, today, false))
                .toList();
        Page<ChallengeAdminDto> contentWithState = contentPage.map(challenge -> withAdminState(challenge, today, false));

        return ChallengePageResponse.of(pinnedWithState, contentWithState);
    }

    public Optional<ChallengeAdminDto> getChallenge(Long id) {
        LocalDate today = LocalDate.now(clock);
        return challengeRepo.findById(id).map(challenge -> withAdminState(challenge, today, true));
    }

    public Optional<ChallengeAdminDto> toggleActive(Long id, boolean active) {
        if (!active) {
            Optional<ChallengeAdminDto> existing = challengeRepo.findById(id);
            if (existing.isPresent() && LocalDate.now(clock).equals(existing.get().challengeDate())) {
                throw new IllegalStateException("Cannot deactivate today's challenge");
            }
        }
        log.info("Toggling active for challenge {} to {}", id, active);
        Optional<ChallengeAdminDto> result = challengeRepo.setActive(id, active);
        if (result.isPresent()) cache.evictOnMutation();
        return result;
    }

    public Optional<ChallengeAdminDto> updateChallenge(Long id, String challengeLevel, String difficulty) {
        log.info("Updating challenge {} to level={}, difficulty={}", id, challengeLevel, difficulty);
        Optional<ChallengeAdminDto> result = challengeRepo.update(id, challengeLevel, difficulty);
        if (result.isPresent()) cache.evictOnMutation();
        return result;
    }

    public List<String> getLevels() {
        return cache.getStringList(cache.levelsKey()).orElseGet(() -> {
            var data = challengeRepo.findDistinctLevels();
            cache.putMeta(cache.levelsKey(), data);
            return data;
        });
    }

    public List<String> getDifficulties() {
        return cache.getStringList(cache.difficultiesKey()).orElseGet(() -> {
            var data = challengeRepo.findDistinctDifficulties();
            cache.putMeta(cache.difficultiesKey(), data);
            return data;
        });
    }

    public Map<String, Object> getStats() {
        return cache.getMap(cache.statsKey()).orElseGet(() -> {
            long total = challengeRepo.count();
            long active = challengeRepo.countByIsActive(true);
            var data = Map.<String, Object>of("totalChallenges", total, "activeChallenges", active, "inactiveChallenges", total - active, "levels", challengeRepo.findDistinctLevels().size(), "difficulties", challengeRepo.findDistinctDifficulties().size());
            cache.putMeta(cache.statsKey(), data);
            return data;
        });
    }

    public long invalidateCache() {
        return cache.evictAll();
    }

    public long invalidateTodayGameplayCache() {
        return cache.evictTodayGameplayCache(LocalDate.now(clock));
    }

    public void invalidateAfterExternalChallengeMutation() {
        cache.evictOnMutation();
        invalidateTodayGameplayCache();
    }

    private ChallengeAdminDto withAdminState(ChallengeAdminDto challenge, LocalDate today, boolean includeDetails) {
        boolean active = Boolean.TRUE.equals(challenge.isActive());
        boolean canRerollCountry = active && challenge.challengeDate().isAfter(today);
        boolean canRerollHint = active && !challenge.challengeDate().isBefore(today);
        String hintsJson = includeDetails ? resolveHintImageUrls(challenge.hintsJson()) : challenge.hintsJson();
        return challenge.withRerollState(canRerollCountry, canRerollHint, hintsJson);
    }

    private String resolveHintImageUrls(String hintsJson) {
        if (hintsJson == null || hintsJson.isBlank()) {
            return hintsJson;
        }

        try {
            List<Map<String, Object>> hints = objectMapper.readValue(hintsJson, HINTS_JSON_REF);
            boolean changed = false;

            for (Map<String, Object> hint : hints) {
                Long hintId = hint.get("hintId") instanceof Number value ? value.longValue() : null;
                Object rawImageUrl = hint.get("imageUrl");
                if (rawImageUrl instanceof String imageUrl && !imageUrl.isBlank()) {
                    String resolved = storageService.resolveHintAssetUrl(hintId, imageUrl);
                    if (!imageUrl.equals(resolved)) {
                        hint.put("imageUrl", resolved);
                        changed = true;
                    }
                }
            }

            return changed ? objectMapper.writeValueAsString(hints) : hintsJson;
        } catch (Exception e) {
            log.warn("Failed to resolve challenge hint images: {}", e.getMessage());
            return hintsJson;
        }
    }
}
