package io.clueterra.questions.flaggame.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import io.clueterra.questions.countrycatalog.model.CatalogStatistic;
import io.clueterra.questions.countrycatalog.model.ValidatedCountry;
import io.clueterra.questions.countrycatalog.repository.CountryCatalogRepository;
import io.clueterra.questions.countrycatalog.service.CountryCatalogReader;
import io.clueterra.questions.countrycatalog.service.CountryFactValidator;
import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.flaggame.model.FlagQuestionDraft;
import io.clueterra.questions.flaggame.repository.FlagCatalogRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.operation.AdminOperationRunner;
import io.clueterra.questions.service.operation.AdminOperationSpec;
import io.clueterra.questions.service.operation.OperationSummary;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FlagGenerationService {

    private static final AdminOperationSpec SPEC = new AdminOperationSpec(
            "flag-game", "Generate Flag Questions", OperationType.CHALLENGE_GENERATION, "FLAG_GAME");
    private static final String CAPITAL_HINT = "CAPITAL";

    private final AdminOperationRunner operationRunner;
    private final CountryCatalogReader reader;
    private final CountryCatalogRepository countryRepository;
    private final CountryFactValidator validator;
    private final FlagQuestionBuilder builder;
    private final FlagCatalogRepository repository;
    private final ConsoleLogWriter console;

    public String startGeneration() {
        return operationRunner.launch(SPEC, context -> generate());
    }

    OperationSummary generate() throws IOException {
        List<CountryCatalogEntry> entries = reader.read();
        Set<String> codes = entries.stream().map(CountryCatalogEntry::iso).collect(Collectors.toSet());
        Set<String> missing = countryRepository.findMissingCountryCodes(codes);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Flag countries missing from Questions: " + missing);
        }
        Map<String, List<CatalogStatistic>> statistics = countryRepository.findStatistics(codes);
        Map<String, ValidatedCountry> validated = entries.stream().collect(Collectors.toMap(
                CountryCatalogEntry::iso,
                entry -> validator.validate(entry, statistics.getOrDefault(entry.iso(), List.of()))));
        List<FlagQuestionDraft> questions = builder.build(entries, validated);
        int changed = repository.replaceCatalog(questions);
        long capitalClues = questions.stream()
                .filter(question -> question.hints().stream().anyMatch(hint -> CAPITAL_HINT.equals(hint.kind())))
                .count();
        console.success("Flag questions ready: " + questions.size() + " countries, " + changed
                + " changed; " + capitalClues + " have a verified capital clue");
        return OperationSummary.of("questions", questions.size())
                .and("changed", changed)
                .and("capitalClues", capitalClues);
    }
}
