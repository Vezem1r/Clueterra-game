package io.clueterra.questions.rivergame.media;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.operation.AdminOperationRunner;
import io.clueterra.questions.service.operation.AdminOperationSpec;
import io.clueterra.questions.service.operation.OperationSummary;
import io.clueterra.questions.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiverPhotoSyncService {

    private static final AdminOperationSpec SPEC = new AdminOperationSpec(
            "river-photos", "Sync River Photos", OperationType.IMAGE_MAINTENANCE, "RIVER_PHOTOS");
    private static final int PROGRESS_INTERVAL = 50;

    private final AdminOperationRunner operationRunner;
    private final RiverWikidataClient wikidataClient;
    private final RiverPhotoResolver photoResolver;
    private final RiverMediaRepository repository;
    private final Optional<ImageStorageService> imageStorageService;
    private final ConsoleLogWriter console;

    public String startSync() {
        return operationRunner.launch(SPEC, context -> sync());
    }

    OperationSummary sync() {
        ImageStorageService storage = imageStorageService.orElseThrow(() ->
                new IllegalStateException("River photos require the hint-images MinIO bucket"));
        List<String> riverKeys = repository.findActiveRiverKeys();
        if (riverKeys.isEmpty()) {
            throw new IllegalStateException("Generate river questions before syncing their photos");
        }
        List<RiverWikidataRecord> records = wikidataClient.fetch(riverKeys);
        console.info("Wikidata returned media links for " + records.size() + " rivers");
        int photos = 0;
        int withoutPhoto = 0;
        int failed = 0;
        for (int index = 0; index < records.size(); index++) {
            RiverWikidataRecord record = records.get(index);
            RiverPhotoResolution resolution = photoResolver.resolve(record, storage);
            switch (resolution.status()) {
                case FOUND -> {
                    repository.save(record.riverKey(), record.wikipediaUrl(), resolution.photo());
                    photos++;
                }
                case NONE -> {
                    repository.save(record.riverKey(), record.wikipediaUrl(), Optional.empty());
                    withoutPhoto++;
                }
                case FAILED -> {
                    log.warn("River photo for {} kept unchanged: {}", record.riverKey(), resolution.reason());
                    failed++;
                }
            }
            if ((index + 1) % PROGRESS_INTERVAL == 0) {
                console.info("River photos checked: " + (index + 1) + " of " + records.size());
            }
        }
        String message = "River photos synced: " + photos + " with a photo, " + withoutPhoto
                + " without a suitable photo, " + failed + " unchanged after source errors";
        if (failed > 0) {
            console.error(message);
        } else {
            console.success(message);
        }
        return OperationSummary.of("rivers", records.size())
                .and("photos", photos)
                .and("withoutPhoto", withoutPhoto)
                .and("failed", failed);
    }
}
