package io.clueterra.questions.quizgame.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.clueterra.questions.quizgame.model.QuizCountryProfile;
import io.clueterra.questions.quizgame.model.QuizKind;
import io.clueterra.questions.countrycatalog.model.CountryFactSheet;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QuizFacts {

    public static final String VALUES = "VALUES";

    private static final String TYPE = "type";
    private static final String ISO = "iso";
    private static final String POPULATION = "POPULATION";
    private static final String AREA = "AREA";
    private static final String HIGHEST_POINT = "HIGHEST_POINT";
    private static final String CAPITAL = "CAPITAL";
    private static final String CURRENCY = "CURRENCY";
    private static final String DRIVES_LEFT = "DRIVES_LEFT";
    private static final String LANDLOCKED = "LANDLOCKED";
    private static final String NEIGHBOURS = "NEIGHBOURS";
    private static final String REGION = "REGION";

    private static final Map<QuizKind, List<String>> PREFERENCES = new EnumMap<>(Map.ofEntries(
            Map.entry(QuizKind.FLAG, List.of(CAPITAL, CURRENCY, POPULATION)),
            Map.entry(QuizKind.CAPITAL, List.of(POPULATION, HIGHEST_POINT, AREA)),
            Map.entry(QuizKind.NEIGHBOUR, List.of(NEIGHBOURS, CAPITAL, AREA)),
            Map.entry(QuizKind.CURRENCY, List.of(POPULATION, CAPITAL, AREA)),
            Map.entry(QuizKind.DOMAIN, List.of(CAPITAL, POPULATION, AREA)),
            Map.entry(QuizKind.LANDLOCKED, List.of(HIGHEST_POINT, AREA, POPULATION)),
            Map.entry(QuizKind.OUTLINE, List.of(AREA, LANDLOCKED, HIGHEST_POINT, POPULATION)),
            Map.entry(QuizKind.NEIGHBOUR_CAPITAL, List.of(CAPITAL, POPULATION, AREA)),
            Map.entry(QuizKind.HIGHEST_POINT, List.of(AREA, DRIVES_LEFT, POPULATION, CAPITAL)),
            Map.entry(QuizKind.NOT_NEIGHBOUR, List.of(NEIGHBOURS, AREA, POPULATION)),
            Map.entry(QuizKind.NEIGHBOUR_COUNT, List.of(NEIGHBOURS, AREA, POPULATION))));

    private final ObjectMapper mapper;

    public JsonNode about(QuizKind kind, QuizCountryProfile profile) {
        return PREFERENCES.getOrDefault(kind, List.of(POPULATION, CAPITAL, AREA)).stream()
                .map(type -> fact(type, profile))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> node(REGION).put("subregion", profile.entry().subregion()));
    }

    public JsonNode values(String metric, List<? extends Number> values) {
        ObjectNode node = node(VALUES).put("metric", metric);
        node.set("values", mapper.valueToTree(values));
        return node;
    }

    public JsonNode drivesLeft(QuizCountryProfile country) {
        return node(DRIVES_LEFT).put(ISO, country.iso());
    }

    private Optional<JsonNode> fact(String type, QuizCountryProfile profile) {
        CountryFactSheet facts = profile.facts();
        return switch (type) {
            case POPULATION -> Optional.ofNullable(facts.population()).map(population -> {
                ObjectNode node = node(POPULATION).put("value", population.value());
                Optional.ofNullable(population.year()).ifPresent(year -> node.put("year", year));
                return node;
            });
            case AREA -> Optional.ofNullable(facts.areaKm2()).map(area -> node(AREA).put("value", Math.round(area.value())));
            case HIGHEST_POINT -> Optional.ofNullable(facts.highestPoint()).map(peak -> {
                ObjectNode node = node(HIGHEST_POINT).put("elevation", peak.elevationM());
                node.set("names", mapper.valueToTree(peak.name()));
                return node;
            });
            case CAPITAL -> Optional.ofNullable(facts.capital()).map(capital -> {
                ObjectNode node = node(CAPITAL);
                node.set("names", mapper.valueToTree(capital));
                return node;
            });
            case CURRENCY -> Optional.ofNullable(profile.currency()).map(currency -> {
                ObjectNode node = node(CURRENCY).put("code", currency.code());
                node.set("names", mapper.valueToTree(QuizText.capitalized(currency.name())));
                return node;
            });
            case DRIVES_LEFT -> Optional.of(profile).filter(country -> QuizFactChecker.LEFT.equals(country.drivingSide()))
                    .map(this::drivesLeft);
            case LANDLOCKED -> Optional.of(facts).filter(CountryFactSheet::landlocked).map(sheet -> node(LANDLOCKED));
            case NEIGHBOURS -> Optional.ofNullable(profile.entry().neighbours()).filter(list -> !list.isEmpty()).map(list -> {
                ObjectNode node = node(NEIGHBOURS);
                node.set("countries", mapper.valueToTree(list));
                return node;
            });
            default -> Optional.empty();
        };
    }

    private ObjectNode node(String type) {
        return mapper.createObjectNode().put(TYPE, type);
    }
}
