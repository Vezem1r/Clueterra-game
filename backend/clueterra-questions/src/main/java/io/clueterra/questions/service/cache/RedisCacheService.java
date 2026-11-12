package io.clueterra.questions.service.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PREFIX = "clueterra:";

    public void put(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(PREFIX + key, value);
        } catch (Exception e) {
            log.debug("[REDIS] Write failed for {}: {}", key, e.getMessage());
        }
    }

    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(stringRedisTemplate.opsForValue().get(PREFIX + key));
        } catch (Exception e) {
            log.debug("[REDIS] Read failed for {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void putMap(String key, Map<String, String> map) {
        try {
            String json = objectMapper.writeValueAsString(map);
            stringRedisTemplate.opsForValue().set(PREFIX + key, json);
        } catch (Exception e) {
            log.debug("[REDIS] Write map failed for {}: {}", key, e.getMessage());
        }
    }

    public Optional<Map<String, String>> getMap(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(PREFIX + key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, new TypeReference<>() {}));
            }
        } catch (Exception e) {
            log.debug("[REDIS] Read map failed for {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public void putCoords(String countryCode, double[] coords) {
        try {
            String json = objectMapper.writeValueAsString(coords);
            stringRedisTemplate.opsForValue().set(PREFIX + "coords:" + countryCode, json);
        } catch (Exception e) {
            log.debug("[REDIS] Write coords failed for {}: {}", countryCode, e.getMessage());
        }
    }

    public Optional<double[]> getCoords(String countryCode) {
        try {
            String json = stringRedisTemplate.opsForValue().get(PREFIX + "coords:" + countryCode);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, double[].class));
            }
        } catch (Exception e) {
            log.debug("[REDIS] Read coords failed for {}: {}", countryCode, e.getMessage());
        }
        return Optional.empty();
    }
}
