package io.clueterra.questions.rivergame.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.rivergame.catalog.RiverCatalogBundle;
import io.clueterra.questions.rivergame.catalog.RiverCatalogCourse;
import io.clueterra.questions.rivergame.catalog.RiverCatalogEntry;
import io.clueterra.questions.rivergame.catalog.RiverCatalogHint;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiverCatalogImportJob {

    private static final String CITY_KIND = "CITY";
    private static final Map<Integer, Set<String>> CLUE_KINDS = Map.of(
            1, Set.of("GEOGRAPHY"),
            2, Set.of(CITY_KIND, "TRIBUTARY", "COUNTRIES"),
            3, Set.of("NAME"));

    private final ObjectMapper mapper;
    private final RiverCatalogRepository repository;
    private final RiverCatalogAssetStore assetStore;
    private final ConsoleLogWriter console;

    @Async
    public void generate(String operationId) {
        try {
            RiverCatalogBundle bundle = readBundle();
            Map<String, List<RiverCatalogEntry>> byCountry = bundle.entries().stream()
                    .collect(Collectors.groupingBy(RiverCatalogEntry::countryCode, TreeMap::new, Collectors.toList()));
            byCountry.forEach(this::validateCountry);
            var missingCountries = repository.findMissingCountryCodes(byCountry.keySet());
            if (!missingCountries.isEmpty()) {
                throw new IllegalStateException("River catalog countries missing from Questions: " + missingCountries);
            }
            bundle.entries().stream()
                    .flatMap(entry -> Stream.of(entry.plainImageKey(), entry.bordersImageKey()))
                    .distinct()
                    .forEach(assetStore::verify);

            int changed = 0;
            for (Map.Entry<String, List<RiverCatalogEntry>> country : byCountry.entrySet()) {
                List<RiverCatalogPreparedEntry> prepared = new ArrayList<>(country.getValue().size());
                for (RiverCatalogEntry entry : country.getValue()) {
                    prepared.add(new RiverCatalogPreparedEntry(entry,
                            assetStore.store(entry.plainImageKey()), assetStore.store(entry.bordersImageKey())));
                }
                changed += repository.replaceCountry(country.getKey(), prepared);
            }
            int retired = bundle.completeCatalog()
                    ? repository.retireAbsentCountries(byCountry.keySet()) : 0;
            long fiveRiverCountries = byCountry.values().stream().filter(entries -> entries.size() == 5).count();
            long distinctRivers = bundle.entries().stream().map(RiverCatalogEntry::riverKey).distinct().count();
            console.success("River questions ready: " + bundle.entries().size() + " country questions ("
                    + distinctRivers + " distinct rivers) in " + byCountry.size() + " countries; "
                    + changed + " question rows refreshed; " + retired + " retired. " + fiveRiverCountries
                    + " countries have five source-backed rivers; "
                    + (byCountry.size() - fiveRiverCountries) + " have fewer.");
            console.completeOperation(operationId);
        } catch (Exception error) {
            log.error("River catalog generation failed", error);
            console.error("River question generation failed: " + error.getMessage());
            console.failOperation(operationId, error.getMessage());
        }
    }

    private RiverCatalogBundle readBundle() throws IOException {
        ClassPathResource resource = new ClassPathResource("assets/river-game/catalog.json");
        try (InputStream input = resource.getInputStream()) {
            RiverCatalogBundle bundle = mapper.readValue(input, RiverCatalogBundle.class);
            if (bundle.entries() == null || bundle.entries().isEmpty()) {
                throw new IllegalStateException("Bundled river catalog is empty");
            }
            return bundle;
        }
    }

    private void validateCountry(String code, List<RiverCatalogEntry> entries) {
        if (!code.matches("[A-Z]{2}") || entries.size() > 5) {
            throw new IllegalArgumentException("Invalid river catalog country: " + code);
        }
        Map<String, RiverCatalogEntry> identities = entries.stream()
                .collect(Collectors.toMap(RiverCatalogEntry::riverKey, Function.identity()));
        if (identities.size() != entries.size()
                || entries.stream().map(RiverCatalogEntry::rankInCountry).distinct().count() != entries.size()) {
            throw new IllegalArgumentException("Duplicate river or rank for " + code);
        }
        for (RiverCatalogEntry entry : entries) {
            if (!code.equals(entry.countryCode()) || entry.rankInCountry() < 1 || entry.rankInCountry() > 5
                    || entry.contentHash() == null || !entry.contentHash().matches("[0-9a-f]{64}")
                    || entry.plainImageKey() == null
                    || !entry.plainImageKey().matches("river-game/[0-9a-f]{64}\\.webp")
                    || entry.bordersImageKey() == null
                    || !entry.bordersImageKey().matches("river-game/[0-9a-f]{64}\\.webp")
                    || entry.plainImageKey().equals(entry.bordersImageKey())
                    || !validCourse(entry.course())
                    || entry.hints() == null || entry.hints().size() != CLUE_KINDS.size()
                    || entry.hints().stream().map(RiverCatalogHint::position).distinct().count() != CLUE_KINDS.size()
                    || !entry.hints().stream().allMatch(this::validHint)
                    || entry.facts() == null || entry.facts().isEmpty()
                    || entry.facts().stream().anyMatch(fact -> fact.kind() == null || fact.text() == null || fact.text().isBlank())) {
                throw new IllegalArgumentException("Invalid river question " + entry.riverKey() + " for " + code);
            }
        }
    }

    private boolean validHint(RiverCatalogHint hint) {
        return CLUE_KINDS.getOrDefault(hint.position(), Set.of()).contains(hint.kind())
                && hint.text() != null && !hint.text().isBlank()
                && (CITY_KIND.equals(hint.kind()) == (hint.place() != null))
                && (hint.place() == null || (hint.place().name() != null && !hint.place().name().isBlank()
                && Math.abs(hint.place().lng()) <= 180 && Math.abs(hint.place().lat()) <= 90));
    }

    private boolean validCourse(RiverCatalogCourse course) {
        return course != null && course.bbox() != null && course.bbox().size() == 4
                && course.coordinates() != null && !course.coordinates().isEmpty()
                && course.coordinates().stream().allMatch(line -> line != null && line.size() >= 2
                && line.stream().allMatch(point -> point != null && point.size() == 2
                && Math.abs(point.get(0)) <= 180 && Math.abs(point.get(1)) <= 90));
    }
}
