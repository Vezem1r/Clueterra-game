package io.clueterra.questions.quizgame.service;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import io.clueterra.questions.countrycatalog.model.CatalogStatistic;
import io.clueterra.questions.countrycatalog.model.ValidatedCountry;
import io.clueterra.questions.countrycatalog.repository.CountryCatalogRepository;
import io.clueterra.questions.countrycatalog.service.CountryCatalogReader;
import io.clueterra.questions.countrycatalog.service.CountryFactValidator;
import io.clueterra.questions.countrycatalog.service.CountryTiers;
import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.quizgame.model.QuizCountryDraft;
import io.clueterra.questions.quizgame.model.QuizCountryProfile;
import io.clueterra.questions.quizgame.model.QuizQuestionDraft;
import io.clueterra.questions.quizgame.repository.QuizCatalogRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.operation.AdminOperationRunner;
import io.clueterra.questions.service.operation.AdminOperationSpec;
import io.clueterra.questions.service.operation.OperationSummary;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizGenerationService {

    private static final AdminOperationSpec SPEC = new AdminOperationSpec(
            "quiz-game", "Generate World Quiz Questions", OperationType.CHALLENGE_GENERATION, "QUIZ_GAME");

    private final AdminOperationRunner operationRunner;
    private final CountryCatalogReader reader;
    private final CountryCatalogRepository countryRepository;
    private final CountryFactValidator validator;
    private final CountryTiers countryTiers;
    private final QuizFactChecker factChecker;
    private final QuizQuestionBuilder builder;
    private final QuizCatalogRepository repository;
    private final ConsoleLogWriter console;

    public String startGeneration() {
        return operationRunner.launch(SPEC, context -> generate());
    }

    OperationSummary generate() throws IOException {
        List<CountryCatalogEntry> entries = reader.read();
        Set<String> codes = entries.stream().map(CountryCatalogEntry::iso).collect(Collectors.toSet());
        Set<String> missing = countryRepository.findMissingCountryCodes(codes);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Quiz countries missing from Questions: " + missing);
        }
        Map<String, List<CatalogStatistic>> statistics = countryRepository.findStatistics(codes);
        Map<String, ValidatedCountry> validated = entries.stream().collect(Collectors.toMap(
                CountryCatalogEntry::iso,
                entry -> validator.validate(entry, statistics.getOrDefault(entry.iso(), List.of()))));
        Map<String, QuizCountryProfile> profiles = factChecker.profiles(
                entries, validated, statistics, countryTiers.tiers(entries, validated));
        List<QuizCountryDraft> countries = builder.build(profiles);
        int changed = repository.replaceCatalog(countries);
        Map<String, Long> kinds = countries.stream()
                .flatMap(country -> country.questions().stream())
                .collect(Collectors.groupingBy(QuizQuestionDraft::kind, TreeMap::new, Collectors.counting()));
        long questions = kinds.values().stream().mapToLong(Long::longValue).sum();
        Map<Integer, Long> levels = countries.stream()
                .collect(Collectors.groupingBy(QuizCountryDraft::levels, TreeMap::new, Collectors.counting()));
        console.success("World quiz ready: " + countries.size() + " countries, " + questions + " questions, "
                + changed + " changed; countries by levels " + levels + "; " + kinds);
        OperationSummary summary = OperationSummary.of("countries", countries.size())
                .and("questions", questions)
                .and("changed", changed);
        levels.forEach((count, total) -> summary.and("countriesWith" + count + "Levels", total));
        kinds.forEach((kind, count) -> summary.and(kind.toLowerCase(Locale.ROOT), count));
        return summary;
    }
}
