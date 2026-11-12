package io.clueterra.api.admin.indicators.service;

import io.clueterra.api.admin.indicators.dto.IndicatorConfigAdminDto;
import io.clueterra.api.admin.indicators.repository.ComboIndicatorConfigJdbcRepository;
import io.clueterra.api.admin.indicators.repository.IndicatorConfigJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndicatorConfigAdminService {

    private static final String KIND_COMBO = "combo";
    private static final String KIND_REGULAR = "regular";

    private final IndicatorConfigJdbcRepository repository;
    private final ComboIndicatorConfigJdbcRepository comboRepository;
    private final IndicatorCacheService cache;

    public Page<IndicatorConfigAdminDto> listIndicators(String kind, int page, int size, String search,
                                                        String category, String profileLevel, String familyType,
                                                        Boolean isActive, String sortBy, String sortDir) {
        String normalizedKind = normalizeKind(kind);
        String profileFacet = isCombo(normalizedKind) ? familyType : profileLevel;
        String cacheKey = cache.listKey(normalizedKind, page, size, search, category, profileFacet, isActive, sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, size);

        Optional<Page<IndicatorConfigAdminDto>> cached = cache.getList(cacheKey, pageable);
        if (cached.isPresent()) {
            return cached.get();
        }

        Page<IndicatorConfigAdminDto> result = isCombo(normalizedKind)
                ? comboRepository.findAll(search, category, familyType, isActive, sortBy, sortDir, page, size)
                : repository.findAll(search, category, profileLevel, isActive, sortBy, sortDir, page, size);
        cache.putList(cacheKey, result);
        return result;
    }

    public List<String> getCategories(String kind) {
        String normalizedKind = normalizeKind(kind);
        return cache.getStringList(cache.categoriesKey(normalizedKind)).orElseGet(() -> {
            List<String> data = isCombo(normalizedKind)
                    ? comboRepository.findDistinctCategories()
                    : repository.findDistinctCategories();
            cache.putMeta(cache.categoriesKey(normalizedKind), data);
            return data;
        });
    }

    public List<String> getProfileLevels(String kind) {
        String normalizedKind = normalizeKind(kind);
        return cache.getStringList(cache.profileLevelsKey(normalizedKind)).orElseGet(() -> {
            List<String> data = isCombo(normalizedKind)
                    ? comboRepository.findDistinctFamilyTypes()
                    : repository.findDistinctProfileLevels();
            cache.putMeta(cache.profileLevelsKey(normalizedKind), data);
            return data;
        });
    }

    public long invalidateCache() {
        return cache.evictAll();
    }

    private String normalizeKind(String kind) {
        return KIND_COMBO.equalsIgnoreCase(kind) ? KIND_COMBO : KIND_REGULAR;
    }

    private boolean isCombo(String kind) {
        return KIND_COMBO.equals(kind);
    }
}
