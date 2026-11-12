package io.clueterra.questions.silhouettegame.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import io.clueterra.questions.countrycatalog.model.CountryFactSheet;
import io.clueterra.questions.countrycatalog.model.CountryRankFact;
import io.clueterra.questions.countrycatalog.model.ValidatedCountry;
import io.clueterra.questions.countrycatalog.service.CountryTiers;
import io.clueterra.questions.silhouettegame.model.SilhouetteHintDraft;
import io.clueterra.questions.silhouettegame.model.SilhouetteQuestionDraft;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SilhouetteQuestionBuilder {

    private static final int RANK_HIGHLIGHT = 10;
    private static final String LARGEST = "LARGEST";
    private static final String SMALLEST = "SMALLEST";
    private static final int LIGHT_CLUE = 1;
    private static final int FULL_CLUE = 2;
    private static final double ROUNDING = 1000.0;

    private final CountryTiers countryTiers;
    private final ObjectMapper mapper;

    public List<SilhouetteQuestionDraft> build(List<CountryCatalogEntry> entries, Map<String, ValidatedCountry> validated,
            Set<String> withPhoto) {
        Map<String, CountryRankFact> areaRanks = ranks(entries, validated, ValidatedCountry::rankArea,
                country -> country.facts().areaKm2() != null);
        Map<String, CountryRankFact> populationRanks = ranks(entries, validated, ValidatedCountry::rankPopulation,
                country -> country.facts().population() != null);
        Map<String, Integer> tiers = countryTiers.tiers(entries, validated);
        Map<String, List<Double>> regionCenters = entries.stream().collect(Collectors.groupingBy(
                CountryCatalogEntry::subregion, Collectors.collectingAndThen(Collectors.toList(), this::regionCenter)));
        return entries.stream().map(entry -> {
            ValidatedCountry country = validated.get(entry.iso());
            CountryFactSheet facts = country.facts().withRanks(areaRanks.get(entry.iso()), populationRanks.get(entry.iso()));
            List<SilhouetteHintDraft> hints = hints(entry, country, regionCenters.get(entry.subregion()),
                    withPhoto.contains(entry.iso()));
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("entry", entry);
            content.put("tier", tiers.get(entry.iso()));
            content.put("facts", facts);
            content.put("hints", hints);
            return new SilhouetteQuestionDraft(entry.iso(), tiers.get(entry.iso()), entry.region(), entry.subregion(),
                    entry.center(), entry.bounds(), entry.names(), entry.shapePath(), entry.flagColors(), facts,
                    hash(content), hints);
        }).toList();
    }

    private List<SilhouetteHintDraft> hints(CountryCatalogEntry entry, ValidatedCountry country, List<Double> regionCenter,
            boolean hasPhoto) {
        List<SilhouetteHintDraft> hints = new ArrayList<>();
        hints.add(new SilhouetteHintDraft(1, "REGION", LIGHT_CLUE, mapper.valueToTree(Map.of(
                "region", entry.region(), "subregion", entry.subregion(), "center", regionCenter))));
        hints.add(new SilhouetteHintDraft(2, "LOCATION", LIGHT_CLUE, mapper.valueToTree(Map.of(
                "center", entry.center(), "bounds", entry.bounds()))));
        hints.add(country.capitalClue() != null
                ? new SilhouetteHintDraft(3, "CAPITAL", FULL_CLUE, mapper.valueToTree(Map.of("capitals", country.capitalClue())))
                : new SilhouetteHintDraft(3, "FLAG_COLORS", FULL_CLUE, mapper.valueToTree(Map.of("colors", entry.flagColors()))));
        if (hasPhoto) {
            hints.add(new SilhouetteHintDraft(4, "LANDMARK", FULL_CLUE, mapper.createObjectNode()));
        }
        return hints;
    }

    private Map<String, CountryRankFact> ranks(List<CountryCatalogEntry> entries, Map<String, ValidatedCountry> validated,
            ToDoubleFunction<ValidatedCountry> value, Predicate<ValidatedCountry> known) {
        List<CountryCatalogEntry> ranked = entries.stream()
                .sorted(Comparator.comparingDouble((CountryCatalogEntry entry) -> value.applyAsDouble(validated.get(entry.iso()))).reversed())
                .toList();
        Map<String, CountryRankFact> result = new LinkedHashMap<>();
        IntStream.range(0, ranked.size()).forEach(index -> {
            String iso = ranked.get(index).iso();
            if (!known.test(validated.get(iso))) {
                return;
            }
            int rank = index + 1;
            int fromEnd = ranked.size() - index;
            if (rank <= RANK_HIGHLIGHT) {
                result.put(iso, new CountryRankFact(rank, LARGEST));
            } else if (fromEnd <= RANK_HIGHLIGHT) {
                result.put(iso, new CountryRankFact(fromEnd, SMALLEST));
            }
        });
        return result;
    }

    private List<Double> regionCenter(List<CountryCatalogEntry> members) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (CountryCatalogEntry member : members) {
            double lng = Math.toRadians(member.center().get(0));
            double lat = Math.toRadians(member.center().get(1));
            x += Math.cos(lat) * Math.cos(lng);
            y += Math.cos(lat) * Math.sin(lng);
            z += Math.sin(lat);
        }
        double lng = Math.toDegrees(Math.atan2(y, x));
        double lat = Math.toDegrees(Math.atan2(z, Math.hypot(x, y)));
        return List.of(Math.round(lng * ROUNDING) / ROUNDING, Math.round(lat * ROUNDING) / ROUNDING);
    }

    private String hash(Object content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException error) {
            throw new IllegalStateException("Could not hash a silhouette question", error);
        }
    }
}
