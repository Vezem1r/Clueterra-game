package io.clueterra.questions.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Slf4j
@Configuration
public class DynamicMappingsConfig {

    private Map<String, String> countryRegions = Map.of();
    private Map<String, String> countryNames = Map.of();
    private Map<String, String> countryIso3 = Map.of();
    private Map<String, String> osmCountryMappings = Map.of();

    private Map<String, String> ciaIsoToFactbook = Map.of();
    private Map<String, String> ciaFactbookToIso = Map.of();

    @PostConstruct
    public void init() {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

        try (InputStream is = new ClassPathResource("mappings/country-reference.yml").getInputStream()) {
            Map<String, Object> data = yaml.readValue(is, Map.class);
            Object raw = data.get("countries");
            if (raw instanceof List<?> list) {
                Map<String, String> regions = new LinkedHashMap<>();
                Map<String, String> names = new LinkedHashMap<>();
                Map<String, String> iso3Map = new LinkedHashMap<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> entry) {
                        String iso2 = safeString(entry.get("iso2"));
                        if (iso2 == null) continue;
                        String region = safeString(entry.get("region"));
                        String name = safeString(entry.get("name"));
                        String iso3 = safeString(entry.get("iso3"));
                        if (region != null) regions.put(iso2, region);
                        if (name != null) names.put(iso2, name);
                        if (iso3 != null) iso3Map.put(iso2, iso3);
                    }
                }
                countryRegions = Map.copyOf(regions);
                countryNames = Map.copyOf(names);
                countryIso3 = Map.copyOf(iso3Map);
                log.info("[MAPPINGS] Loaded {} country references (regions/names/iso3)",
                        countryRegions.size());
            }
        } catch (Exception e) {
            log.error("[MAPPINGS] Failed to load country-reference.yml", e);
        }

        try (InputStream is = new ClassPathResource("mappings/osm-country-mappings.yml").getInputStream()) {
            Map<String, Object> data = yaml.readValue(is, Map.class);
            Object rawMappings = data.get("mappings");
            if (rawMappings instanceof Map<?, ?> raw) {
                Map<String, String> mappings = new LinkedHashMap<>();
                raw.forEach((k, v) -> {
                    String key = safeString(k);
                    mappings.put(key, v.toString());
                });
                osmCountryMappings = Map.copyOf(mappings);
            }
            log.info("[MAPPINGS] Loaded {} OSM country mappings", osmCountryMappings.size());
        } catch (Exception e) {
            log.error("[MAPPINGS] Failed to load osm-country-mappings.yml", e);
        }

        try (InputStream is = new ClassPathResource("mappings/ciafactbook-country-codes.yml").getInputStream()) {
            Map<String, Object> data = yaml.readValue(is, Map.class);
            Object rawMapping = data.get("iso-to-factbook");
            if (rawMapping instanceof Map<?, ?> raw) {
                Map<String, String> isoToFactbook = new LinkedHashMap<>();
                Map<String, String> factbookToIso = new LinkedHashMap<>();
                raw.forEach((k, v) -> {
                    String iso = k.toString();
                    String cia = v.toString();
                    isoToFactbook.put(iso, cia);
                    factbookToIso.put(cia, iso);
                });
                ciaIsoToFactbook = Map.copyOf(isoToFactbook);
                ciaFactbookToIso = Map.copyOf(factbookToIso);
            }
            log.info("[MAPPINGS] Loaded {} CIA Factbook country codes", ciaIsoToFactbook.size());
        } catch (Exception e) {
            log.error("[MAPPINGS] Failed to load ciafactbook-country-codes.yml", e);
        }
    }

    private static String safeString(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean b) return b ? "ON" : "NO";
        return value.toString();
    }
}
