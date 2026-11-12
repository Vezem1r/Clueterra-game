package io.clueterra.questions.rivergame.generation;

import io.clueterra.questions.service.console.ConsoleLogWriter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RiverCatalogGenerationService {

    private final ConsoleLogWriter console;
    private final RiverCatalogImportJob importJob;

    public String startGeneration() {
        String operationId = "river-game-" + UUID.randomUUID().toString().substring(0, 8);
        console.startOperation(operationId, "Generate River Game Questions");
        importJob.generate(operationId);
        return operationId;
    }
}
