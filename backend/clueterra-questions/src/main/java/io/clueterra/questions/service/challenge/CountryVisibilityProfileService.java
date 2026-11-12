package io.clueterra.questions.service.challenge;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import io.clueterra.questions.config.CountryVisibilityProfile;
import io.clueterra.questions.entity.enums.RegionGroup;
import io.clueterra.questions.entity.enums.VisibilityTier;

@Service
@Slf4j
public class CountryVisibilityProfileService {

    private Map<String, CountryVisibilityProfile> profiles = Map.of();
    private CountryVisibilityProfile defaultProfile;

    @PostConstruct
    public void loadProfiles() {
        try (InputStream is =
                     new ClassPathResource("country-visibility-profile.yml").getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);

            defaultProfile = parseProfile(castMap(data.get("default")));

            Map<String, CountryVisibilityProfile> loaded = new HashMap<>();
            Map<String, Object> countries = castMap(data.get("countries"));
            if (countries != null) {
                for (Map.Entry<String, Object> entry : countries.entrySet()) {
                    Map<String, Object> raw = castMap(entry.getValue());
                    if (raw != null) {
                        loaded.put(entry.getKey().toUpperCase(), parseProfile(raw));
                    }
                }
            }

            profiles = Map.copyOf(loaded);
            log.info("Loaded {} country visibility profiles", profiles.size());
        } catch (Exception e) {
            log.error("Failed to load country-visibility-profile.yml", e);
            defaultProfile =
                    new CountryVisibilityProfile(1.0, VisibilityTier.HARD, false, RegionGroup.OTHER);
        }
    }

    public CountryVisibilityProfile getProfile(String iso2) {
        if (iso2 == null) return defaultProfile;
        return profiles.getOrDefault(iso2.toUpperCase(), defaultProfile);
    }

    public BigDecimal getModifier(String iso2) {
        return BigDecimal.valueOf(getProfile(iso2).score());
    }

    public Set<String> getEasyEligibleCountries() {
        return profiles.entrySet().stream()
                .filter(e -> e.getValue().easyEligible())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Set<String> getCountriesByTier(VisibilityTier tier) {
        return profiles.entrySet().stream()
                .filter(e -> e.getValue().visibilityTier() == tier)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Set<String> getCountriesByRegion(RegionGroup region) {
        return profiles.entrySet().stream()
                .filter(e -> e.getValue().regionGroup() == region)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private CountryVisibilityProfile parseProfile(Map<String, Object> raw) {
        double score = toDouble(raw.getOrDefault("score", 1.0));
        VisibilityTier tier =
                VisibilityTier.valueOf(raw.getOrDefault("visibilityTier", "HARD").toString());
        boolean easyEligible = Boolean.TRUE.equals(raw.get("easyEligible"));
        RegionGroup region =
                RegionGroup.valueOf(raw.getOrDefault("regionGroup", "OTHER").toString());
        return new CountryVisibilityProfile(score, tier, easyEligible, region);
    }

    private double toDouble(Object value) {
        if (value instanceof Number num) return num.doubleValue();
        return Double.parseDouble(value.toString());
    }

    private Map<String, Object> castMap(Object obj) {
        if (obj instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return null;
    }
}
