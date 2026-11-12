package io.clueterra.questions.countryphotos.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.clueterra.questions.countryphotos.client.CountryLandmarkClient;
import io.clueterra.questions.countryphotos.model.CommonsFileInfo;
import io.clueterra.questions.countryphotos.model.CountryPhotoCandidate;
import io.clueterra.questions.countryphotos.model.CountryPhotoLookup;
import io.clueterra.questions.countryphotos.model.CountryPhotoResolution;
import io.clueterra.questions.countryphotos.model.LandmarkInfo;
import io.clueterra.questions.countryphotos.repository.CountryPhotoRepository;
import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.operation.AdminOperationRunner;
import io.clueterra.questions.service.operation.AdminOperationSpec;
import io.clueterra.questions.service.operation.OperationSummary;
import io.clueterra.questions.silhouettegame.service.SilhouetteGenerationService;
import io.clueterra.questions.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CountryPhotoSyncService {

    private static final AdminOperationSpec SPEC = new AdminOperationSpec(
            "country-photos", "Sync Country Photos", OperationType.IMAGE_MAINTENANCE, "COUNTRY_PHOTOS");
    private static final int PROGRESS_INTERVAL = 25;

    private final AdminOperationRunner operationRunner;
    private final CountryPhotoRepository repository;
    private final CountryLandmarkClient landmarkClient;
    private final CountryPhotoRanker ranker;
    private final CountryPhotoResolver resolver;
    private final Optional<ImageStorageService> imageStorageService;
    private final SilhouetteGenerationService silhouetteGenerationService;
    private final ConsoleLogWriter console;

    public String startSync() {
        return operationRunner.launch(SPEC, context -> sync());
    }

    OperationSummary sync() {
        ImageStorageService storage = imageStorageService.orElseThrow(() ->
                new IllegalStateException("Country photos require the hint-images MinIO bucket"));
        List<CountryPhotoCandidate> candidates = repository.findCandidates();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No landmark hint photos are available for the silhouette countries; "
                    + "generate the silhouette questions first");
        }
        Set<String> manual = repository.findManualCountries();
        List<CountryPhotoCandidate> automatic = candidates.stream()
                .filter(candidate -> !manual.contains(candidate.iso2()))
                .toList();
        CountryPhotoLookup lookup = lookup(automatic);
        console.info("Commons described " + lookup.files().size() + " candidate photos; "
                + lookup.landmarks().size() + " depict a known landmark");

        Map<String, List<CountryPhotoCandidate>> byCountry = automatic.stream()
                .collect(Collectors.groupingBy(CountryPhotoCandidate::iso2));
        int photos = 0;
        int withoutPhoto = 0;
        int failed = 0;
        int index = 0;
        for (Map.Entry<String, List<CountryPhotoCandidate>> country : byCountry.entrySet()) {
            CountryPhotoResolution resolution = resolver.resolve(ranker.rank(country.getValue(),
                    lookup.files(), lookup.depicted(), lookup.landmarks()), storage);
            switch (resolution.status()) {
                case FOUND -> {
                    repository.save(country.getKey(), resolution.photo());
                    photos++;
                }
                case NONE -> {
                    log.info("Country {} has no suitable landmark photo: {}", country.getKey(), resolution.reason());
                    repository.save(country.getKey(), Optional.empty());
                    withoutPhoto++;
                }
                case FAILED -> {
                    log.warn("Country photo for {} kept unchanged: {}", country.getKey(), resolution.reason());
                    failed++;
                }
            }
            if (++index % PROGRESS_INTERVAL == 0) {
                console.info("Country photos checked: " + index + " of " + byCountry.size());
            }
        }
        String message = "Country photos synced: " + photos + " with a photo, " + withoutPhoto
                + " without a suitable photo, " + failed + " unchanged after source errors, "
                + manual.size() + " chosen by an administrator kept";
        if (failed > 0) {
            console.error(message);
        } else {
            console.success(message);
        }
        boolean cluesRefreshed = silhouetteGenerationService.refreshLandmarkClues();
        return OperationSummary.of("countries", byCountry.size())
                .and("photos", photos)
                .and("withoutPhoto", withoutPhoto)
                .and("failed", failed)
                .and("manual", manual.size())
                .and("silhouetteCluesRefreshed", cluesRefreshed);
    }

    private CountryPhotoLookup lookup(List<CountryPhotoCandidate> candidates) {
        Map<String, CommonsFileInfo> files = landmarkClient.files(candidates.stream()
                .map(candidate -> "File:" + ranker.title(candidate.sourcePageUrl())).toList());
        Map<Long, String> depicted = landmarkClient.depictedLandmarks(files.values().stream().map(CommonsFileInfo::pageId).toList());
        Map<String, LandmarkInfo> landmarks = landmarkClient.landmarks(List.copyOf(depicted.values()));
        return new CountryPhotoLookup(files, depicted, landmarks);
    }
}
