package io.clueterra.api.assets.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class FlagAssetService {

    private static final String SVG_CONTENT_TYPE = "image/svg+xml";
    private static final String ASSET_BASE_PATH = "assets/flags/";

    @Cacheable(value = "flagImages", key = "#iso2 == null ? 'null' : #iso2.toLowerCase()")
    public FlagAssetPayload fetchFlag(String iso2) {
        String normalizedIso2 = normalizeIso2(iso2);
        ClassPathResource resource = new ClassPathResource(ASSET_BASE_PATH + normalizedIso2 + ".svg");
        if (!resource.exists()) {
            throw new ResponseStatusException(NOT_FOUND, "Flag not found");
        }

        try {
            byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return new FlagAssetPayload(bytes, MediaType.parseMediaType(SVG_CONTENT_TYPE).toString());
        } catch (IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to load flag asset");
        }
    }

    public Map<String, String> fetchFlags(List<String> codes) {
        Map<String, String> flags = new LinkedHashMap<>();
        codes.stream()
                .map(this::normalizeIso2)
                .distinct()
                .filter(code -> new ClassPathResource(ASSET_BASE_PATH + code + ".svg").exists())
                .forEach(code -> flags.put(code, new String(fetchFlag(code).bytes(), StandardCharsets.UTF_8)));
        return flags;
    }

    private String normalizeIso2(String iso2) {
        if (iso2 == null || !iso2.matches("^[A-Za-z]{2}$")) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid ISO-2 country code");
        }
        return iso2.toLowerCase(Locale.ROOT);
    }
}
