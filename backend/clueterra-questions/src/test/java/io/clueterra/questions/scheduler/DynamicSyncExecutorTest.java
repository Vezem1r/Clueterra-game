package io.clueterra.questions.scheduler;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.source.SourceModule;
import io.clueterra.questions.service.source.SourceModuleRegistry;
import io.clueterra.questions.service.sync.SourceSyncFlowService;
import io.clueterra.questions.service.sync.SourceSyncResult;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicSyncExecutorTest {

    @Test
    void execute_delegatesToUnifiedSourceFlow() {
        SourceModuleRegistry sourceModuleRegistry = mock(SourceModuleRegistry.class);
        SourceSyncFlowService sourceSyncFlowService = mock(SourceSyncFlowService.class);
        ConsoleLogWriter console = mock(ConsoleLogWriter.class);
        DynamicSyncExecutor executor = new DynamicSyncExecutor(sourceModuleRegistry, sourceSyncFlowService, console);

        DataSourceClient client = mock(DataSourceClient.class);
        SourceModule module = mock(SourceModule.class);
        SourceSyncResult result = new SourceSyncResult(
                DataSource.GLOBAL_BRANDS, 1, 1, 1, 0, 0
        );

        when(client.isAvailable()).thenReturn(true);
        when(client.getSource()).thenReturn(DataSource.GLOBAL_BRANDS);
        when(sourceModuleRegistry.require(DataSource.GLOBAL_BRANDS)).thenReturn(module);
        when(sourceSyncFlowService.syncFull(module, null, "dynamic-global_brands")).thenReturn(result);

        executor.execute(client);

        verify(sourceModuleRegistry).require(DataSource.GLOBAL_BRANDS);
        verify(sourceSyncFlowService).syncFull(module, null, "dynamic-global_brands");
        verify(console).info("GLOBAL_BRANDS sync: unified source flow start");
    }
}
