package io.clueterra.questions.scheduler;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.source.SourceModule;
import io.clueterra.questions.service.source.SourceModuleRegistry;
import io.clueterra.questions.service.sync.SourceSyncFlowService;
import io.clueterra.questions.service.sync.SourceSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicSyncExecutor {

    private final SourceModuleRegistry sourceModuleRegistry;
    private final SourceSyncFlowService sourceSyncFlowService;
    private final ConsoleLogWriter console;

    public void execute(DataSourceClient client) {
        String sourceName = client.getSource().name();
        String tag = "[" + sourceName + "]";

        if (!client.isAvailable()) {
            log.debug("{} Disabled, skipping", tag);
            return;
        }

        SourceModule module = sourceModuleRegistry.require(client.getSource());
        try {
            console.info(sourceName + " sync: unified source flow start");
            SourceSyncResult result = sourceSyncFlowService.syncFull(module, null, "dynamic-" + client.getSource().name().toLowerCase());
            log.info("{} Completed: {} countries, {} stats, {} hints",
                    tag, result.countriesProcessed(), result.statisticsSaved(), result.hintsGenerated());
            if (result.hasFailures()) {
                log.warn("{} Completed with {} country-level failures", tag, result.failures().size());
                console.error(sourceName + " sync completed with " + result.failures().size() + " error(s)");
            }
        } catch (RuntimeException e) {
            log.error("{} Sync failed", tag, e);
            console.error(sourceName + " sync failed: " + e.getMessage());
            throw e;
        }
    }
}
