package io.clueterra.questions.service.challenge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ChallengeJsonCodec {

    private static final TypeReference<List<ChallengeCountryEntry>> COUNTRIES = new TypeReference<>() {
    };
    private static final TypeReference<List<ChallengeHintEntry>> HINTS = new TypeReference<>() {
    };
    private static final Comparator<ChallengeHintEntry> HINT_ORDER = Comparator
            .comparingInt(ChallengeHintEntry::countryOrder)
            .thenComparingInt(ChallengeHintEntry::hintOrder);

    private final ObjectMapper objectMapper;

    public List<ChallengeCountryEntry> readCountries(String json) {
        return read(json, COUNTRIES);
    }

    public List<ChallengeHintEntry> readHints(String json) {
        return read(json, HINTS);
    }

    public String writeCountries(List<ChallengeCountryEntry> countries) {
        List<ChallengeCountryEntry> sorted = new ArrayList<>(countries);
        sorted.sort(Comparator.comparingInt(ChallengeCountryEntry::order));
        return write(sorted);
    }

    public String writeHints(List<ChallengeHintEntry> hints) {
        List<ChallengeHintEntry> sorted = new ArrayList<>(hints);
        sorted.sort(HINT_ORDER);
        return write(sorted);
    }

    private <T> List<T> read(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse challenge JSON", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize challenge JSON", e);
        }
    }
}
