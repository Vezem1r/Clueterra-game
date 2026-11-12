package io.clueterra.questions.flaggame.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import io.clueterra.questions.countrycatalog.model.ValidatedCountry;
import io.clueterra.questions.countrycatalog.service.CountryTiers;
import io.clueterra.questions.flaggame.model.FlagHintDraft;
import io.clueterra.questions.flaggame.model.FlagQuestionDraft;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FlagQuestionBuilder {

    private static final int REGION_POSITION = 1;
    private static final int INITIAL_POSITION = 2;
    private static final int CAPITAL_POSITION = 3;
    private static final int LIGHT_CLUE = 1;
    private static final int FULL_CLUE = 2;

    private final CountryTiers countryTiers;
    private final ObjectMapper mapper;

    public List<FlagQuestionDraft> build(List<CountryCatalogEntry> entries, Map<String, ValidatedCountry> validated) {
        Map<String, Integer> tiers = countryTiers.tiers(entries, validated);
        return entries.stream().map(entry -> {
            List<FlagHintDraft> hints = hints(entry, validated.get(entry.iso()));
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("iso", entry.iso());
            content.put("names", entry.names());
            content.put("region", entry.region());
            content.put("subregion", entry.subregion());
            content.put("tier", tiers.get(entry.iso()));
            content.put("hints", hints);
            return new FlagQuestionDraft(entry.iso(), tiers.get(entry.iso()), entry.region(), entry.subregion(),
                    entry.names(), hash(content), hints);
        }).toList();
    }

    private List<FlagHintDraft> hints(CountryCatalogEntry entry, ValidatedCountry country) {
        List<FlagHintDraft> hints = new ArrayList<>();
        hints.add(new FlagHintDraft(REGION_POSITION, "REGION", LIGHT_CLUE, mapper.valueToTree(Map.of(
                "region", entry.region(), "subregion", entry.subregion()))));
        hints.add(new FlagHintDraft(INITIAL_POSITION, "INITIAL", LIGHT_CLUE, mapper.valueToTree(Map.of(
                "letters", initials(entry.names())))));
        if (country.capitalClue() != null) {
            hints.add(new FlagHintDraft(CAPITAL_POSITION, "CAPITAL", FULL_CLUE, mapper.valueToTree(Map.of(
                    "capitals", country.capitalClue()))));
        }
        return hints;
    }

    private Map<String, String> initials(Map<String, String> names) {
        return names.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                name -> name.getValue().substring(0, name.getValue().offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT),
                (first, second) -> first,
                LinkedHashMap::new));
    }

    private String hash(Object content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException error) {
            throw new IllegalStateException("Could not hash a flag question", error);
        }
    }
}
