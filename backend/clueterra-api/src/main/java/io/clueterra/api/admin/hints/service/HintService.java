package io.clueterra.api.admin.hints.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.admin.hints.dto.CreateHintRequest;
import io.clueterra.api.admin.hints.dto.HintAdminDto;
import io.clueterra.api.admin.hints.dto.HintCountryOption;
import io.clueterra.api.admin.hints.dto.HintImageUpdate;
import io.clueterra.api.admin.hints.dto.ReplaceHintImageRequest;
import io.clueterra.api.admin.hints.dto.UpdateHintRequest;
import io.clueterra.api.admin.hints.image.DownloadedWikimediaImage;
import io.clueterra.api.admin.hints.image.WikimediaCommonsImageClient;
import io.clueterra.api.admin.hints.image.WikimediaHintImage;
import io.clueterra.api.admin.hints.image.WikimediaImageException;
import io.clueterra.api.admin.hints.repository.AdminHintJdbcRepository;
import io.clueterra.api.admin.hints.repository.HintCountryJdbcRepository;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class HintService {

    private final AdminHintJdbcRepository hintRepo;
    private final HintCountryJdbcRepository countryRepo;
    private final HintCacheService cache;
    private final StorageService storageService;
    private final WikimediaCommonsImageClient wikimediaImageClient;

    public Page<HintAdminDto> listHints(int page, int size, String search, List<Long> countryIds, String category, String hintType, String source, Boolean active, Integer difficultyMin, Integer difficultyMax, Integer sourceYear, Boolean hasImage, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size);
        String key = cache.listKey(page, size, search, countryIds, category, hintType, source, active, difficultyMin, difficultyMax, sourceYear, hasImage, sortBy, sortDir);
        Optional<Page<HintAdminDto>> cached = cache.getList(key, pageable);
        if (cached.isPresent()) return cached.get();
        Page<HintAdminDto> result = hintRepo.findAll(search, countryIds, category, hintType, source, active, difficultyMin, difficultyMax, sourceYear, hasImage, sortBy, sortDir, pageable);
        cache.putList(key, result);
        return result;
    }

    public Optional<HintAdminDto> createHint(CreateHintRequest request) {
        log.info("Creating manual hint for country {}: category={}, type={}", request.countryId(), request.category(), request.hintType());
        Optional<HintAdminDto> result = hintRepo.insert(request);
        if (result.isPresent()) cache.evictOnMutation();
        return result;
    }

    public Optional<HintAdminDto> getHint(Long id) {
        return hintRepo.findById(id);
    }

    @Transactional
    public Optional<HintAdminDto> updateHint(Long id, UpdateHintRequest request) {
        log.info("Updating hint ID {}: {}", id, request);
        if (Boolean.TRUE.equals(request.isActive())) {
            hintRepo.resolveIdentitySuppression(id);
        } else if (Boolean.FALSE.equals(request.isActive())) {
            hintRepo.createIdentitySuppression(id, "QUARANTINE", "Hint deactivated by an administrator");
        }
        Optional<HintAdminDto> result = hintRepo.update(id, request.hintText(), request.difficulty(), request.qualityScore(), request.isActive());
        if (result.isPresent()) cache.evictOnMutation();
        return result;
    }

    @Transactional
    public Optional<HintAdminDto> toggleActive(Long id, boolean active) {
        log.info("Toggling active status for hint ID {} to {}", id, active);
        if (active) {
            hintRepo.resolveIdentitySuppression(id);
        } else {
            hintRepo.createIdentitySuppression(id,"QUARANTINE", "Hint deactivated by an administrator");
        }
        Optional<HintAdminDto> result = hintRepo.setActive(id, active);
        if (result.isPresent()) cache.evictOnMutation();
        return result;
    }

    public Optional<HintAdminDto> replaceHintImage(Long id, ReplaceHintImageRequest request) {
        Optional<HintAdminDto> existingHint = hintRepo.findById(id);
        if (existingHint.isEmpty()) {
            return Optional.empty();
        }
        HintAdminDto hint = existingHint.get();
        if (!"IMAGE_FACT".equals(hint.hintType())) {
            throw new IllegalArgumentException("Images can only be assigned to IMAGE_FACT hints");
        }

        log.info("Replacing image for hint ID {} from Wikimedia Commons file {}", id, request.fileTitle());
        WikimediaHintImage wikimediaImage = wikimediaImageClient.findExactImage(request.fileTitle());
        DownloadedWikimediaImage downloadedImage = wikimediaImageClient.download(wikimediaImage);
        String countryCode = hint.countryCode() == null
                ? "unknown"
                : hint.countryCode().toLowerCase(Locale.ROOT);
        String objectPath = "wikimedia-admin/" + countryCode + "/" + id + "/"
                + wikimediaImage.sourceSha1() + "." + downloadedImage.extension();

        String imageUrl;
        try {
            imageUrl = storageService.uploadToHintBucket(
                    objectPath,
                    new ByteArrayInputStream(downloadedImage.bytes()),
                    downloadedImage.bytes().length,
                    downloadedImage.contentType());
        } catch (RuntimeException exception) {
            log.error("Failed to store Wikimedia image for hint {}: {}", id, exception.getMessage(), exception);
            throw new WikimediaImageException(
                    HttpStatus.BAD_GATEWAY,
                    "The Wikimedia image was valid but could not be stored",
                    exception);
        }

        HintImageUpdate update = new HintImageUpdate(
                imageUrl,
                wikimediaImage.originalUrl(),
                wikimediaImage.sourcePageUrl(),
                wikimediaImage.authorName(),
                wikimediaImage.attributionText(),
                wikimediaImage.licenseLabel(),
                wikimediaImage.licenseUrl(),
                revisionHash(hint.hintText(), imageUrl));
        Optional<HintAdminDto> result = hintRepo.updateImage(id, update);
        if (result.isPresent()) cache.evictOnMutation();
        return result;
    }

    private static String revisionHash(String hintText, String imageUrl) {
        String revision = (hintText == null ? "" : hintText.trim())
                + "\n--image--\n"
                + (imageUrl == null ? "" : imageUrl.trim());
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(revision.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Transactional
    public Optional<HintAdminDto> deleteHintImage(Long id) {
        log.info("Deleting image for hint ID {}", id);
        hintRepo.createImageSuppression(id, "Hint image detached by an administrator");
        Optional<HintAdminDto> result = hintRepo.clearImageFields(id);
        if (result.isPresent()) cache.evictOnMutation();
        return result;
    }

    @Transactional
    public boolean softDelete(Long id) {
        log.info("Soft-deleting hint ID {}", id);
        hintRepo.createIdentitySuppression(id, "DELETE", "Hint deleted by an administrator");
        boolean deleted = hintRepo.softDelete(id);
        if (deleted) cache.evictOnMutation();
        return deleted;
    }

    public List<String> getCategories() {
        return cache.getStringList(cache.categoriesKey()).orElseGet(() -> {
            var data = hintRepo.findDistinctCategories();
            cache.putMeta(cache.categoriesKey(), data);
            return data;
        });
    }

    public List<String> getHintTypes() {
        return cache.getStringList(cache.typesKey()).orElseGet(() -> {
            var data = hintRepo.findDistinctHintTypes();
            cache.putMeta(cache.typesKey(), data);
            return data;
        });
    }

    public List<String> getSources() {
        return cache.getStringList(cache.sourcesKey()).orElseGet(() -> {
            var data = hintRepo.findDistinctSources();
            cache.putMeta(cache.sourcesKey(), data);
            return data;
        });
    }

    public List<Integer> getSourceYears() {
        return cache.getIntList(cache.yearsKey()).orElseGet(() -> {
            var data = hintRepo.findDistinctSourceYears();
            cache.putMeta(cache.yearsKey(), data);
            return data;
        });
    }

    public List<HintCountryOption> getCountriesForFilter(String search) {
        return countryRepo.findCountriesWithHints(search);
    }

    public Map<String, Object> getStats() {
        return cache.getMap(cache.statsKey()).orElseGet(() -> {
            long total = hintRepo.count();
            long active = hintRepo.countByIsActive(true);
            var data = Map.<String, Object>of(
                    "totalHints", total,
                    "activeHints", active,
                    "inactiveHints", total - active,
                    "categories", hintRepo.findDistinctCategories().size(),
                    "types", hintRepo.findDistinctHintTypes().size(),
                    "sources", hintRepo.findDistinctSources().size());
            cache.putMeta(cache.statsKey(), data);
            return data;
        });
    }

    public long invalidateCache() {
        return cache.evictAll();
    }
}
