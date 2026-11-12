package io.clueterra.questions.ai.tooling;

import io.clueterra.questions.ai.tooling.dto.AiStoredImageToolResponse;
import io.clueterra.questions.storage.ImageStorageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AiImageStorageToolService {

    private final Optional<ImageStorageService> imageStorageService;

    public AiImageStorageToolService(Optional<ImageStorageService> imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    public AiStoredImageToolResponse storeCandidateImageInternal(String countryCode, String sourceUrl, String hintKey) {
        if (StringUtils.isBlank(sourceUrl)) {
            return new AiStoredImageToolResponse(false, sourceUrl, null, "Source image URL is blank");
        }

        if (imageStorageService.isEmpty()) {
            return new AiStoredImageToolResponse(false, sourceUrl, sourceUrl, "Image storage is disabled");
        }

        String normalizedCountryCode = StringUtils.defaultIfBlank(countryCode, "unknown")
                .trim()
                .toLowerCase(Locale.ROOT);
        String normalizedKey = StringUtils.defaultIfBlank(hintKey, "candidate")
                .replaceAll("[^a-zA-Z0-9-_]+", "-")
                .toLowerCase(Locale.ROOT);
        String objectPath = "ai/" + normalizedCountryCode + "/" + normalizedKey + ".jpg";

        Optional<String> storedUrl = imageStorageService.get().downloadAndStore(sourceUrl, objectPath);
        return storedUrl
                .map(url -> new AiStoredImageToolResponse(true, sourceUrl, url, "Stored candidate image"))
                .orElseGet(() -> new AiStoredImageToolResponse(false, sourceUrl, null, "Failed to store candidate image"));
    }
}
