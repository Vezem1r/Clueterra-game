package io.clueterra.questions.service.common;

import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.service.cache.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CountryCodeCache {

    private static final String ISO3_TO_ISO2_KEY = "country-codes:iso3-to-iso2";
    private static final String ISO2_TO_ISO3_KEY = "country-codes:iso2-to-iso3";

    private final RedisCacheService redis;

    public void updateFromRawData(List<RawCountryData> countries) {
        Map<String, String> forward = redis.getMap(ISO3_TO_ISO2_KEY).orElseGet(HashMap::new);
        Map<String, String> reverse = redis.getMap(ISO2_TO_ISO3_KEY).orElseGet(HashMap::new);
        int added = 0;

        for (RawCountryData country : countries) {
            String iso2 = country.getIsoAlpha2();
            String iso3 = country.getIsoAlpha3();
            if (iso2 != null && iso3 != null && !iso2.isBlank() && !iso3.isBlank()) {
                String key3 = iso3.toUpperCase();
                String key2 = iso2.toUpperCase();
                if (!forward.containsKey(key3)) {
                    added++;
                }
                forward.put(key3, key2);
                reverse.put(key2, key3);
            }
        }

        redis.putMap(ISO3_TO_ISO2_KEY, forward);
        redis.putMap(ISO2_TO_ISO3_KEY, reverse);

        log.info("[COUNTRY_CODE_CACHE] {} total mappings ({} new)", forward.size(), added);
    }

    public Optional<String> iso3ToIso2(String iso3) {
        if (iso3 == null) return Optional.empty();
        return redis.getMap(ISO3_TO_ISO2_KEY)
                .map(m -> m.get(iso3.toUpperCase()));
    }

    public int size() {
        return redis.getMap(ISO3_TO_ISO2_KEY).map(Map::size).orElse(0);
    }
}
