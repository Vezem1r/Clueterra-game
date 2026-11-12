package io.clueterra.questions.clients.fastfood;

import io.clueterra.questions.clients.fastfood.dto.CountryFastFood;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.clueterra.questions.clients.fastfood.dto.FastFoodData;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

@Getter
@Slf4j
@Component
public class FastFoodConfigLoader {

    private FastFoodData data;

    @PostConstruct
    public void init() {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try (InputStream is = new ClassPathResource("mappings/fastfood-regional.yml").getInputStream()) {
            data = yaml.readValue(is, FastFoodData.class);
            int countries = data != null && data.countries() != null ? data.countries().size() : 0;
            log.info("[FAST_FOOD] Loaded fast food data for {} countries", countries);
        } catch (Exception e) {
            log.error("[FAST_FOOD] Failed to load fastfood-regional.yml", e);
            data = new FastFoodData(Map.of());
        }
    }

    public Optional<CountryFastFood> getCountryData(String isoAlpha2) {
        if (data == null || data.countries() == null) return Optional.empty();
        return Optional.ofNullable(data.countries().get(isoAlpha2));
    }
}
