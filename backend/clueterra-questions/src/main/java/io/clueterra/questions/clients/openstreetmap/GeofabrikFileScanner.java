package io.clueterra.questions.clients.openstreetmap;

import io.clueterra.questions.clients.openstreetmap.dto.CountryPbfFile;
import io.clueterra.questions.config.DynamicMappingsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeofabrikFileScanner {

    private final DynamicMappingsConfig mappingsConfig;

    private static final String PBF_EXTENSION = ".osm.pbf";
    private static final Set<String> MINOR_WORDS = Set.of("and", "of", "the", "in", "on", "at", "to", "for");

    public List<CountryPbfFile> scanDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            log.warn("Directory does not exist: {}", directory);
            return Collections.emptyList();
        }

        try (Stream<Path> files = Files.list(directory)) {
            List<CountryPbfFile> result = files
                    .filter(this::isPbfFile)
                    .map(this::tryMapToCountry)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

            log.info("Found {} country PBF files in {}", result.size(), directory);
            return result;

        } catch (IOException e) {
            log.error("Failed to scan directory: {}", directory, e);
            return Collections.emptyList();
        }
    }

    private boolean isPbfFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(PBF_EXTENSION);
    }

    private Optional<CountryPbfFile> tryMapToCountry(Path path) {
        String fileName = path.getFileName().toString();
        String baseName = StringUtils.removeEnd(fileName, PBF_EXTENSION);

        String countryName = normalizeToCountryName(baseName);
        String countryCode = mappingsConfig.getOsmCountryMappings().get(countryName);

        if (StringUtils.isBlank(countryCode)) {
            log.warn("Unable to map file to country: {} (parsed as '{}')", fileName, countryName);
            return Optional.empty();
        }

        log.debug("Mapped {} -> {} ({})", fileName, countryName, countryCode);
        return Optional.of(new CountryPbfFile(path, countryCode, countryName));
    }

    private String normalizeToCountryName(String baseName) {
        String cleaned = baseName.replaceAll("-\\d+$", "");
        String normalized = cleaned.replace("-", " ").replace("_", " ");
        return capitalizeWords(normalized);
    }

    private String capitalizeWords(String input) {
        if (StringUtils.isBlank(input)) {
            return input;
        }

        String[] words = input.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase();

            if (MINOR_WORDS.contains(word) && i > 0) {
                result.append(word);
            } else {
                result.append(StringUtils.capitalize(word));
            }

            if (i < words.length - 1) {
                result.append(" ");
            }
        }

        return result.toString();
    }
}
