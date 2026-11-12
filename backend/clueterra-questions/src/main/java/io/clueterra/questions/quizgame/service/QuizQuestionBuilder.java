package io.clueterra.questions.quizgame.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.clueterra.questions.quizgame.model.QuizCountryDraft;
import io.clueterra.questions.quizgame.model.QuizCountryProfile;
import io.clueterra.questions.quizgame.model.QuizKind;
import io.clueterra.questions.quizgame.model.QuizMetric;
import io.clueterra.questions.quizgame.model.QuizQuestionDraft;
import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import io.clueterra.questions.countrycatalog.model.CountryCurrency;
import io.clueterra.questions.countrycatalog.model.CountryFlagColor;
import io.clueterra.questions.countrycatalog.model.CountryPeakFact;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QuizQuestionBuilder {

    private static final int WRONG_OPTIONS = 3;
    private static final int OPTION_COUNT = WRONG_OPTIONS + 1;
    private static final int LOOKALIKE_FLAGS = 2;
    private static final int OUTLINE_SUBJECT_POOL = 4;
    private static final int MIN_NOT_NEIGHBOURS = 3;
    private static final int MAX_LEVELS = 3;
    private static final int QUESTIONS_PER_VISIT = 2;
    private static final int[] COUNT_OFFSETS = {-1, 1, 2, -2, 3, 4};
    private static final int MIN_COUNT = 1;
    private static final double TENTHS = 10.0;
    private static final int HEX_RADIX = 16;
    private static final int RED = 1;
    private static final int GREEN = 3;
    private static final int BLUE = 5;
    private static final int CHANNEL_LENGTH = 2;
    private static final String ENGLISH = "en";
    private static final String RUSSIAN = "ru";
    private static final long MIN_PEAK_ELEVATION_M = 500;
    private static final Pattern LATIN = Pattern.compile("\\p{IsLatin}");
    private static final Set<QuizMetric> DECIMAL_METRICS = Set.of(QuizMetric.LIFE_EXPECTANCY, QuizMetric.FOREST, QuizMetric.MEDIAN_AGE);
    private static final String TYPE = "type";
    private static final String NAMES = "names";
    private static final String ISO = "iso";
    private static final String CENTRE_MISSING = "Catalog entry %s has no centre";

    private final QuizDistractors distractors;
    private final QuizFacts facts;
    private final ObjectMapper mapper;

    public List<QuizCountryDraft> build(Map<String, QuizCountryProfile> profiles) {
        List<QuizCountryProfile> all = profiles.values().stream()
                .sorted(Comparator.comparing(QuizCountryProfile::iso))
                .toList();
        return all.stream().map(profile -> country(profile, all, profiles)).toList();
    }

    private QuizCountryDraft country(QuizCountryProfile profile, List<QuizCountryProfile> all, Map<String, QuizCountryProfile> byIso) {
        List<QuizCountryProfile> neighbours = neighbours(profile, byIso);
        Stream<Optional<QuizQuestionDraft>> single = Stream.of(
                flag(profile, all),
                capital(profile, all),
                neighbour(profile, all, neighbours),
                currency(profile, all),
                leftDriving(profile, all),
                domain(profile, all),
                landlocked(profile, all),
                outline(profile, all),
                neighbourCapital(profile, all, neighbours),
                highestPoint(profile, all),
                notNeighbour(profile, all, neighbours),
                neighbourCount(profile));
        Stream<Optional<QuizQuestionDraft>> comparisons = Arrays.stream(QuizMetric.values())
                .map(metric -> compare(metric, profile, all));
        List<QuizQuestionDraft> questions = leveled(Stream.concat(single, comparisons).flatMap(Optional::stream).toList());
        int levels = questions.stream().mapToInt(QuizQuestionDraft::level).max().orElse(0);
        CountryCatalogEntry entry = profile.entry();
        if (entry.center() == null || entry.center().size() < 2) {
            throw new IllegalStateException(CENTRE_MISSING.formatted(entry.iso()));
        }
        List<String> neighbourCodes = neighbours.stream().map(QuizCountryProfile::iso).toList();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put(ISO, entry.iso());
        content.put("tier", profile.tier());
        content.put("levels", levels);
        content.put("region", entry.region());
        content.put("subregion", entry.subregion());
        content.put(NAMES, entry.names());
        content.put("center", entry.center());
        content.put("neighbours", neighbourCodes);
        content.put("questions", questions.stream().map(QuizQuestionDraft::contentHash).toList());
        return new QuizCountryDraft(entry.iso(), profile.tier(), levels, entry.region(), entry.subregion(), entry.names(),
                entry.center().get(0), entry.center().get(1), neighbourCodes, hash(content), questions);
    }

    private List<QuizQuestionDraft> leveled(List<QuizQuestionDraft> questions) {
        List<QuizQuestionDraft> ordered = questions.stream()
                .sorted(Comparator.comparingDouble(QuizQuestionDraft::score).thenComparing(QuizQuestionDraft::kind))
                .toList();
        int levels = Math.min(MAX_LEVELS, ordered.size() / QUESTIONS_PER_VISIT);
        if (levels == 0) {
            return List.of();
        }
        return IntStream.range(0, ordered.size())
                .mapToObj(index -> rehash(ordered.get(index).atLevel(index * levels / ordered.size() + 1)))
                .toList();
    }

    private List<QuizCountryProfile> neighbours(QuizCountryProfile profile, Map<String, QuizCountryProfile> byIso) {
        return Optional.ofNullable(profile.entry().neighbours()).orElse(List.of()).stream()
                .map(byIso::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private Optional<QuizQuestionDraft> flag(QuizCountryProfile profile, List<QuizCountryProfile> all) {
        List<QuizCountryProfile> lookalikes = all.stream()
                .filter(candidate -> !candidate.iso().equals(profile.iso()))
                .sorted(Comparator.comparingDouble((QuizCountryProfile candidate) ->
                                flagDistance(profile.entry().flagColors(), candidate.entry().flagColors()))
                        .thenComparing(QuizCountryProfile::iso))
                .limit(LOOKALIKE_FLAGS)
                .toList();
        Set<String> taken = lookalikes.stream().map(QuizCountryProfile::iso).collect(Collectors.toSet());
        List<JsonNode> wrong = Stream.concat(lookalikes.stream(), ranked(profile, all, QuizKind.FLAG).stream()
                        .filter(candidate -> !taken.contains(candidate.iso())))
                .limit(WRONG_OPTIONS)
                .map(this::flagOption)
                .toList();
        return choice(QuizKind.FLAG, profile, flagOption(profile), wrong);
    }

    private Optional<QuizQuestionDraft> capital(QuizCountryProfile profile, List<QuizCountryProfile> all) {
        Map<String, String> capital = profile.facts().capital();
        if (capital == null || !translated(capital) || sharesName(capital, profile.entry().names())) {
            return Optional.empty();
        }
        return choice(QuizKind.CAPITAL, profile, place(capital), capitals(profile, all, QuizKind.CAPITAL, capital));
    }

    private Optional<QuizQuestionDraft> neighbourCapital(QuizCountryProfile profile, List<QuizCountryProfile> all,
                                                         List<QuizCountryProfile> neighbours) {
        Optional<QuizCountryProfile> subject = distractors.shuffled(neighbours, distractors.seed(profile.iso(), QuizKind.NEIGHBOUR_CAPITAL))
                .stream()
                .filter(candidate -> candidate.facts().capital() != null && translated(candidate.facts().capital()))
                .filter(candidate -> !sharesName(candidate.facts().capital(), candidate.entry().names()))
                .min(Comparator.comparingInt(QuizCountryProfile::tier));
        if (subject.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> capital = subject.get().facts().capital();
        List<JsonNode> wrong = capitals(subject.get(), all, QuizKind.NEIGHBOUR_CAPITAL, capital);
        return choice(QuizKind.NEIGHBOUR_CAPITAL, profile, subjectOf(subject.get()), place(capital), wrong);
    }

    private List<JsonNode> capitals(QuizCountryProfile around, List<QuizCountryProfile> all, QuizKind kind, Map<String, String> answer) {
        return distinct(ranked(around, all, kind).stream()
                        .map(candidate -> candidate.facts().capital())
                        .filter(Objects::nonNull)
                        .filter(this::translated)
                        .filter(other -> !other.get(ENGLISH).equals(answer.get(ENGLISH))),
                other -> other.get(ENGLISH)).stream()
                .map(this::place)
                .toList();
    }

    private Optional<QuizQuestionDraft> neighbour(QuizCountryProfile profile, List<QuizCountryProfile> all,
                                                  List<QuizCountryProfile> neighbours) {
        if (neighbours.isEmpty()) {
            return Optional.empty();
        }
        QuizCountryProfile answer = distractors.shuffled(neighbours, distractors.seed(profile.iso(), QuizKind.NEIGHBOUR)).stream()
                .min(Comparator.comparingInt(QuizCountryProfile::tier))
                .orElseThrow();
        return choice(QuizKind.NEIGHBOUR, profile, countryOption(answer), strangers(profile, all, neighbours, QuizKind.NEIGHBOUR, WRONG_OPTIONS));
    }

    private Optional<QuizQuestionDraft> notNeighbour(QuizCountryProfile profile, List<QuizCountryProfile> all,
                                                     List<QuizCountryProfile> neighbours) {
        if (neighbours.size() < MIN_NOT_NEIGHBOURS) {
            return Optional.empty();
        }
        List<JsonNode> bordering = distractors.shuffled(neighbours, distractors.seed(profile.iso(), QuizKind.NOT_NEIGHBOUR)).stream()
                .limit(WRONG_OPTIONS)
                .map(this::countryOption)
                .toList();
        return strangers(profile, all, neighbours, QuizKind.NOT_NEIGHBOUR, 1).stream()
                .findFirst()
                .flatMap(stranger -> choice(QuizKind.NOT_NEIGHBOUR, profile, stranger, bordering));
    }

    private List<JsonNode> strangers(QuizCountryProfile profile, List<QuizCountryProfile> all, List<QuizCountryProfile> neighbours,
                                     QuizKind kind, int count) {
        Set<String> bordering = neighbours.stream().map(QuizCountryProfile::iso).collect(Collectors.toSet());
        return ranked(profile, all, kind).stream()
                .filter(candidate -> !bordering.contains(candidate.iso()))
                .limit(count)
                .map(this::countryOption)
                .toList();
    }

    private Optional<QuizQuestionDraft> neighbourCount(QuizCountryProfile profile) {
        Integer count = profile.borderCount();
        if (count == null) {
            return Optional.empty();
        }
        List<Integer> values = Stream.concat(Stream.of(count), Arrays.stream(COUNT_OFFSETS)
                        .mapToObj(offset -> count + offset)
                        .filter(value -> value >= MIN_COUNT)
                        .limit(WRONG_OPTIONS))
                .sorted()
                .toList();
        List<JsonNode> options = values.stream().map(this::numberOption).toList();
        return Optional.of(draft(QuizKind.NEIGHBOUR_COUNT, profile, null, options, values.indexOf(count),
                facts.about(QuizKind.NEIGHBOUR_COUNT, profile)));
    }

    private Optional<QuizQuestionDraft> currency(QuizCountryProfile profile, List<QuizCountryProfile> all) {
        CountryCurrency currency = profile.currency();
        if (currency == null) {
            return Optional.empty();
        }
        Set<String> usedHere = profile.entry().reference().currencies().stream()
                .map(CountryCurrency::code)
                .collect(Collectors.toSet());
        List<JsonNode> wrong = distinct(ranked(profile, all, QuizKind.CURRENCY).stream()
                        .map(QuizCountryProfile::currency)
                        .filter(Objects::nonNull)
                        .filter(other -> !usedHere.contains(other.code()))
                        .filter(other -> !other.name().get(ENGLISH).equals(currency.name().get(ENGLISH))),
                other -> other.name().get(ENGLISH)).stream()
                .map(this::currencyOption)
                .toList();
        return choice(QuizKind.CURRENCY, profile, currencyOption(currency), wrong);
    }

    private Optional<QuizQuestionDraft> leftDriving(QuizCountryProfile profile, List<QuizCountryProfile> all) {
        List<QuizCountryProfile> nearby = ranked(profile, all, QuizKind.LEFT_DRIVING);
        Optional<QuizCountryProfile> answer = QuizFactChecker.LEFT.equals(profile.drivingSide())
                ? Optional.of(profile)
                : nearby.stream()
                        .filter(candidate -> QuizFactChecker.LEFT.equals(candidate.drivingSide()))
                        .filter(candidate -> candidate.entry().region().equals(profile.entry().region()))
                        .findFirst();
        if (answer.isEmpty()) {
            return Optional.empty();
        }
        List<JsonNode> wrong = Stream.concat(Stream.of(profile), nearby.stream())
                .filter(candidate -> QuizFactChecker.RIGHT.equals(candidate.drivingSide()))
                .limit(WRONG_OPTIONS)
                .map(this::countryOption)
                .toList();
        if (wrong.size() < WRONG_OPTIONS) {
            return Optional.empty();
        }
        List<JsonNode> options = distractors.shuffled(Stream.concat(Stream.of(countryOption(answer.get())), wrong.stream()).toList(),
                distractors.seed(profile.iso(), QuizKind.LEFT_DRIVING));
        return Optional.of(draft(QuizKind.LEFT_DRIVING, profile, null, options, options.indexOf(countryOption(answer.get())),
                facts.drivesLeft(answer.get())));
    }

    private Optional<QuizQuestionDraft> domain(QuizCountryProfile profile, List<QuizCountryProfile> all) {
        if (profile.domain() == null) {
            return Optional.empty();
        }
        List<JsonNode> wrong = ranked(profile, all, QuizKind.DOMAIN).stream()
                .map(QuizCountryProfile::domain)
                .filter(Objects::nonNull)
                .limit(WRONG_OPTIONS)
                .map(this::textOption)
                .toList();
        return choice(QuizKind.DOMAIN, profile, textOption(profile.domain()), wrong);
    }

    private Optional<QuizQuestionDraft> outline(QuizCountryProfile profile, List<QuizCountryProfile> all) {
        Optional<QuizCountryProfile> subject = ranked(profile, all, QuizKind.OUTLINE).stream()
                .filter(candidate -> candidate.entry().shapePath() != null)
                .limit(OUTLINE_SUBJECT_POOL)
                .min(Comparator.comparingInt(QuizCountryProfile::tier));
        if (subject.isEmpty()) {
            return Optional.empty();
        }
        QuizCountryProfile shown = subject.get();
        List<JsonNode> wrong = distractors.ranked(shown, all, distractors.seed(profile.iso(), QuizKind.OUTLINE)).stream()
                .filter(candidate -> candidate.entry().shapePath() != null)
                .filter(candidate -> !candidate.iso().equals(profile.iso()))
                .limit(WRONG_OPTIONS)
                .map(this::shapeOption)
                .toList();
        return choice(QuizKind.OUTLINE, profile, subjectOf(shown), shapeOption(shown), wrong);
    }

    private Optional<QuizQuestionDraft> landlocked(QuizCountryProfile profile, List<QuizCountryProfile> all) {
        List<QuizCountryProfile> nearby = ranked(profile, all, QuizKind.LANDLOCKED);
        Optional<QuizCountryProfile> answer = nearby.stream()
                .filter(candidate -> candidate.facts().landlocked())
                .filter(candidate -> candidate.entry().region().equals(profile.entry().region()))
                .findFirst();
        if (answer.isEmpty()) {
            return Optional.empty();
        }
        List<JsonNode> wrong = nearby.stream()
                .filter(QuizCountryProfile::coastal)
                .limit(WRONG_OPTIONS)
                .map(this::countryOption)
                .toList();
        return choice(QuizKind.LANDLOCKED, profile, countryOption(answer.get()), wrong);
    }

    private Optional<QuizQuestionDraft> compare(QuizMetric metric, QuizCountryProfile profile, List<QuizCountryProfile> all) {
        Function<QuizCountryProfile, Double> value = candidate -> candidate.metric(metric);
        if (value.apply(profile) == null) {
            return Optional.empty();
        }
        List<QuizCountryProfile> chosen = new ArrayList<>(List.of(profile));
        ranked(profile, all, metric.kind()).stream()
                .filter(candidate -> value.apply(candidate) != null)
                .forEach(candidate -> {
                    if (chosen.size() < OPTION_COUNT && chosen.stream().allMatch(picked ->
                            metric.distinct(value.apply(picked), value.apply(candidate)))) {
                        chosen.add(candidate);
                    }
                });
        if (chosen.size() < OPTION_COUNT) {
            return Optional.empty();
        }
        List<QuizCountryProfile> order = distractors.shuffled(chosen, distractors.seed(profile.iso(), metric.kind()));
        Comparator<QuizCountryProfile> byValue = Comparator.comparingDouble(value::apply);
        QuizCountryProfile winner = metric.highestWins() ? order.stream().max(byValue).orElseThrow() : order.stream().min(byValue).orElseThrow();
        List<Double> values = order.stream().map(candidate -> rounded(metric, value.apply(candidate))).toList();
        return Optional.of(draft(metric.kind(), profile, null, order.stream().map(this::countryOption).toList(), order.indexOf(winner),
                facts.values(metric.name(), values)));
    }

    private double rounded(QuizMetric metric, double value) {
        return DECIMAL_METRICS.contains(metric) ? Math.round(value * TENTHS) / TENTHS : Math.round(value);
    }

    private Optional<QuizQuestionDraft> highestPoint(QuizCountryProfile profile, List<QuizCountryProfile> all) {
        CountryPeakFact peak = profile.facts().highestPoint();
        if (!notablePeak(peak)) {
            return Optional.empty();
        }
        List<JsonNode> wrong = distinct(ranked(profile, all, QuizKind.HIGHEST_POINT).stream()
                        .map(candidate -> candidate.facts().highestPoint())
                        .filter(this::notablePeak)
                        .filter(other -> !other.name().get(ENGLISH).equals(peak.name().get(ENGLISH))),
                other -> other.name().get(ENGLISH)).stream()
                .map(other -> place(other.name()))
                .toList();
        return choice(QuizKind.HIGHEST_POINT, profile, place(peak.name()), wrong);
    }

    private Optional<QuizQuestionDraft> choice(QuizKind kind, QuizCountryProfile profile, JsonNode answer, List<JsonNode> wrong) {
        return choice(kind, profile, null, answer, wrong);
    }

    private Optional<QuizQuestionDraft> choice(QuizKind kind, QuizCountryProfile profile, JsonNode subject, JsonNode answer,
                                               List<JsonNode> wrong) {
        if (wrong.size() < WRONG_OPTIONS) {
            return Optional.empty();
        }
        List<JsonNode> options = distractors.shuffled(Stream.concat(Stream.of(answer), wrong.stream()).toList(),
                distractors.seed(profile.iso(), kind));
        return Optional.of(draft(kind, profile, subject, options, options.indexOf(answer), facts.about(kind, profile)));
    }

    private QuizQuestionDraft draft(QuizKind kind, QuizCountryProfile profile, JsonNode subject, List<JsonNode> options,
                                    int answerIndex, JsonNode fact) {
        return new QuizQuestionDraft(kind.name(), kind.score(profile.tier()), 0, subject, options, answerIndex, fact, null);
    }

    private QuizQuestionDraft rehash(QuizQuestionDraft draft) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("kind", draft.kind());
        content.put("level", draft.level());
        content.put("subject", draft.subject());
        content.put("options", draft.options());
        content.put("answerIndex", draft.answerIndex());
        content.put("fact", draft.fact());
        return new QuizQuestionDraft(draft.kind(), draft.score(), draft.level(), draft.subject(), draft.options(),
                draft.answerIndex(), draft.fact(), hash(content));
    }

    private JsonNode subjectOf(QuizCountryProfile profile) {
        ObjectNode node = mapper.createObjectNode().put(ISO, profile.iso());
        node.set(NAMES, mapper.valueToTree(profile.entry().names()));
        return node;
    }

    private List<QuizCountryProfile> ranked(QuizCountryProfile profile, List<QuizCountryProfile> all, QuizKind kind) {
        return distractors.ranked(profile, all, distractors.seed(profile.iso(), kind));
    }

    private <T> List<T> distinct(Stream<T> candidates, Function<T, String> key) {
        Map<String, T> unique = new LinkedHashMap<>();
        candidates.forEach(candidate -> unique.putIfAbsent(key.apply(candidate), candidate));
        return unique.values().stream().limit(WRONG_OPTIONS).toList();
    }

    private boolean notablePeak(CountryPeakFact peak) {
        return peak != null && peak.elevationM() >= MIN_PEAK_ELEVATION_M && translated(peak.name());
    }

    private boolean translated(Map<String, String> names) {
        String russian = names.get(RUSSIAN);
        return russian != null && !LATIN.matcher(russian).find();
    }

    private boolean sharesName(Map<String, String> capital, Map<String, String> country) {
        return capital.entrySet().stream().anyMatch(name -> {
            String place = name.getValue().toLowerCase(Locale.ROOT);
            String state = country.getOrDefault(name.getKey(), "").toLowerCase(Locale.ROOT);
            return !state.isEmpty() && (place.contains(state) || state.contains(place));
        });
    }

    private double flagDistance(List<CountryFlagColor> first, List<CountryFlagColor> second) {
        return coverage(first, second) + coverage(second, first);
    }

    private double coverage(List<CountryFlagColor> from, List<CountryFlagColor> to) {
        return from.stream()
                .mapToDouble(color -> color.share() * to.stream()
                        .mapToDouble(other -> colorDistance(color.color(), other.color()))
                        .min()
                        .orElse(Double.MAX_VALUE))
                .sum();
    }

    private double colorDistance(String first, String second) {
        return Math.sqrt(IntStream.of(RED, GREEN, BLUE)
                .mapToDouble(start -> channel(first, start) - channel(second, start))
                .map(delta -> delta * delta)
                .sum());
    }

    private int channel(String hex, int start) {
        return Integer.parseInt(hex.substring(start, start + CHANNEL_LENGTH), HEX_RADIX);
    }

    private ObjectNode option(String type) {
        return mapper.createObjectNode().put(TYPE, type);
    }

    private JsonNode countryOption(QuizCountryProfile profile) {
        ObjectNode node = option("COUNTRY").put(ISO, profile.iso());
        node.set(NAMES, mapper.valueToTree(profile.entry().names()));
        return node;
    }

    private JsonNode place(Map<String, String> names) {
        ObjectNode node = option("PLACE");
        node.set(NAMES, mapper.valueToTree(names));
        return node;
    }

    private JsonNode currencyOption(CountryCurrency currency) {
        ObjectNode node = option("CURRENCY").put("code", currency.code());
        node.set(NAMES, mapper.valueToTree(QuizText.capitalized(currency.name())));
        return node;
    }

    private JsonNode flagOption(QuizCountryProfile profile) {
        return option("FLAG").put(ISO, profile.iso());
    }

    private JsonNode shapeOption(QuizCountryProfile profile) {
        return option("SHAPE").put(ISO, profile.iso()).put("shape", profile.entry().shapePath());
    }

    private JsonNode numberOption(int value) {
        return option("NUMBER").put("value", value);
    }

    private JsonNode textOption(String text) {
        return option("TEXT").put("text", text);
    }

    private String hash(Object content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException error) {
            throw new IllegalStateException("Could not hash a quiz question", error);
        }
    }
}
