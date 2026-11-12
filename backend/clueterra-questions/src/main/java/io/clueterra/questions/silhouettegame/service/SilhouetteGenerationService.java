package io.clueterra.questions.silhouettegame.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.clueterra.questions.countrycatalog.model.CatalogStatistic;
import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import io.clueterra.questions.countrycatalog.model.ValidatedCountry;
import io.clueterra.questions.countrycatalog.repository.CountryCatalogRepository;
import io.clueterra.questions.countrycatalog.service.CountryCatalogReader;
import io.clueterra.questions.countrycatalog.service.CountryFactValidator;
import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.operation.AdminOperationRunner;
import io.clueterra.questions.service.operation.AdminOperationSpec;
import io.clueterra.questions.service.operation.OperationSummary;
import io.clueterra.questions.silhouettegame.model.SilhouetteQuestionDraft;
import io.clueterra.questions.silhouettegame.repository.SilhouetteCatalogRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SilhouetteGenerationService {

    private static final AdminOperationSpec SPEC = new AdminOperationSpec(
            "silhouette-game", "Generate Silhouette Questions", OperationType.CHALLENGE_GENERATION, "SILHOUETTE_GAME");

    private final AdminOperationRunner operationRunner;
    private final SilhouetteCatalogRepository repository;
    private final CountryCatalogRepository countryRepository;
    private final CountryFactValidator validator;
    private final SilhouetteQuestionBuilder builder;
    private final CountryCatalogReader reader;
    private final ConsoleLogWriter console;

    public String startGeneration() {
        return operationRunner.launch(SPEC, context -> generate());
    }

    public boolean refreshLandmarkClues() {
        if (!repository.hasQuestions()) {
            return false;
        }
        try {
            generate();
            return true;
        } catch (IOException error) {
            throw new UncheckedIOException("Could not read the bundled silhouette catalog", error);
        }
    }

    OperationSummary generate() throws IOException {
        List<CountryCatalogEntry> entries = reader.read();
        Set<String> codes = entries.stream().map(CountryCatalogEntry::iso).collect(Collectors.toSet());
        Set<String> missing = countryRepository.findMissingCountryCodes(codes);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Silhouette countries missing from Questions: " + missing);
        }
        Map<String, List<CatalogStatistic>> statistics = countryRepository.findStatistics(codes);
        Map<String, ValidatedCountry> validated = entries.stream().collect(Collectors.toMap(
                CountryCatalogEntry::iso,
                entry -> validator.validate(entry, statistics.getOrDefault(entry.iso(), List.of()))));
        Set<String> withPhoto = repository.findCountriesWithPhoto();
        List<SilhouetteQuestionDraft> questions = builder.build(entries, validated, withPhoto);
        int changed = repository.replaceCatalog(questions);

        Map<String, Long> facts = Map.of(
                "capital", count(validated, country -> country.facts().capital() != null),
                "population", count(validated, country -> country.facts().population() != null),
                "area", count(validated, country -> country.facts().areaKm2() != null),
                "highestPoint", count(validated, country -> country.facts().highestPoint() != null),
                "capitalClue", count(validated, country -> country.capitalClue() != null));
        long landmarkClues = questions.stream().filter(question -> question.hints().size() == 4).count();
        console.success("Silhouette questions ready: " + questions.size() + " countries, " + changed
                + " changed; verified facts " + facts + "; " + landmarkClues + " have a landmark photo clue");
        return OperationSummary.of("questions", questions.size())
                .and("changed", changed)
                .and("capitalClues", facts.get("capitalClue"))
                .and("landmarkClues", landmarkClues);
    }

    private long count(Map<String, ValidatedCountry> validated, Predicate<ValidatedCountry> test) {
        return validated.values().stream().filter(test).count();
    }
}
