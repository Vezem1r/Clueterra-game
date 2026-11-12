package io.clueterra.questions.clients.mapillary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.clueterra.questions.clients.mapillary.dto.MapillaryCitiesFile;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MapillaryCityProvider {

    private static final String RESOURCE_PATH = "mapillary-cities.yml";
    private static final String LANDMARK_RESOURCE_PATH = "mapillary-landmarks.yml";

    private Map<String, List<double[]>> citiesByCountry = Map.of();
    private Map<String, List<double[]>> ruralByCountry = Map.of();
    private Map<String, List<double[]>> landmarksByCountry = Map.of();

    @PostConstruct
    void init() {
        try (InputStream is = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
            MapillaryCitiesFile file = yaml.readValue(is, MapillaryCitiesFile.class);
            if (file.cities() != null) {
                citiesByCountry = Collections.unmodifiableMap(new LinkedHashMap<>(file.cities()));
            }
            if (file.rural() != null) {
                ruralByCountry = Collections.unmodifiableMap(new LinkedHashMap<>(file.rural()));
            }
            try (InputStream landmarksInput = new ClassPathResource(LANDMARK_RESOURCE_PATH).getInputStream()) {
                MapillaryCitiesFile generated = yaml.readValue(landmarksInput, MapillaryCitiesFile.class);
                if (generated.landmarks() != null) {
                    landmarksByCountry = Collections.unmodifiableMap(new LinkedHashMap<>(generated.landmarks()));
                }
            }
            int cityTotal = citiesByCountry.values().stream().mapToInt(List::size).sum();
            int ruralTotal = ruralByCountry.values().stream().mapToInt(List::size).sum();
            int landmarkTotal = landmarksByCountry.values().stream().mapToInt(List::size).sum();
            log.info("[MapillaryCityProvider] Loaded {} landmarks across {} countries, plus {} cities and {} rural seeds",
                    landmarkTotal, landmarksByCountry.size(), cityTotal, ruralTotal);
        } catch (Exception e) {
            log.warn("[MapillaryCityProvider] Failed to load {}", RESOURCE_PATH, e);
        }
    }

    public List<double[]> getCities(String countryCode) {
        return citiesByCountry.getOrDefault(countryCode, List.of());
    }

    public List<double[]> getRural(String countryCode) {
        return ruralByCountry.getOrDefault(countryCode, List.of());
    }

    public List<double[]> getLandmarks(String countryCode) {
        return landmarksByCountry.getOrDefault(countryCode, List.of());
    }
}
